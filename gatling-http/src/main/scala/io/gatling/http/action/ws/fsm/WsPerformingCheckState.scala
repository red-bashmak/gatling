/*
 * Copyright 2011-2025 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.http.action.ws.fsm

import io.gatling.commons.stats.{ KO, OK, Status }
import io.gatling.commons.util.Throwables._
import io.gatling.commons.validation.{ Failure, Success }
import io.gatling.core.action.Action
import io.gatling.core.check.Check
import io.gatling.core.session.Session
import io.gatling.http.action.ws.WsInboundMessage
import io.gatling.http.check.ws.{ WsFrameCheck, WsFrameCheckSequence }
import io.gatling.http.client.WebSocket

import com.typesafe.scalalogging.StrictLogging

final case class WsPerformingCheckState(
    fsm: WsFsm,
    webSocket: WebSocket,
    currentCheck: WsFrameCheck,
    remainingChecks: List[WsFrameCheck],
    checkSequenceStart: Long,
    remainingCheckSequences: List[WsFrameCheckSequence[WsFrameCheck]],
    remainingReconnects: Int,
    session: Session,
    next: Either[Action, SendFrame],
    actionName: String,
    requestMessage: Option[String]
) extends WsState(fsm)
    with StrictLogging {
  import fsm._

  override def onTimeout(): NextWsState = {
    logger.debug("Check timeout")
    // check timeout
    // fail check, send next and goto Idle
    val errorMessage = s"Check ${currentCheck.resolvedName} timeout"
    val newSession = logCheckResult(session, clock.nowMillis, KO, None, Some(errorMessage))
    val nextAction = next match {
      case Left(n) =>
        logger.debug("Check timeout, failing it and performing next action")
        fsm.wsLogger.logCheck(
          actionName,
          session,
          KO,
          Some("Check timeout"),
          Some(currentCheck.resolvedName),
          requestMessage
        )
        n
      case Right(sendFrame) =>
        // logging crash
        logger.debug("Check timeout while trying to reconnect, failing pending send message and performing next action")
        fsm.wsLogger.logCheck(
          actionName,
          session,
          KO,
          Some(s"Couldn't reconnect: $errorMessage"),
          Some(currentCheck.resolvedName),
          requestMessage
        )
        statsEngine.logRequestCrash(session.scenario, session.groups, sendFrame.actionName, s"Couldn't reconnect: $errorMessage")
        sendFrame.next
    }

    NextWsState(
      new WsIdleState(
        fsm,
        newSession,
        webSocket,
        remainingReconnects
      ),
      () => nextAction ! newSession
    )
  }

  override def onTextFrameReceived(message: String, timestamp: Long): NextWsState = {
    wsLogger.registerInboundMessage(message, timestamp)
    if (autoReplyTextFrames(message, webSocket)) {
      NextWsState(this)
    } else {
      currentCheck match {
        case WsFrameCheck.Text(_, matchConditions, checks, _, _) =>
          tryApplyingChecks(message, timestamp, matchConditions, checks)

        case _ =>
          logger.debug(s"Received unmatched text frame $message")
          unmatchedInboundMessageBuffer.addOne(WsInboundMessage.Text(timestamp, message))
          // server unmatched message, just log
          logUnmatchedServerMessage(session)
          NextWsState(this)
      }
    }
  }

  override def onBinaryFrameReceived(message: Array[Byte], timestamp: Long): NextWsState = {
    wsLogger.registerInboundMessage(message, timestamp)
    currentCheck match {
      case WsFrameCheck.Binary(_, matchConditions, checks, _, _) =>
        tryApplyingChecks(message, timestamp, matchConditions, checks)

      case _ =>
        logger.debug("Received unmatched binary frame")
        unmatchedInboundMessageBuffer.addOne(WsInboundMessage.Binary(timestamp, message))
        // server unmatched message, just log
        logUnmatchedServerMessage(session)
        NextWsState(this)
    }
  }

  override def onWebSocketClosed(code: Int, reason: String, timestamp: Long): NextWsState = {
    // unexpected close, fail check
    logger.debug(s"WebSocket remotely closed ($code/$reason) while in $stateName state")
    handleWebSocketCheckCrash(session, next, Some(Integer.toString(code)), reason)
  }

  override def onWebSocketCrashed(t: Throwable, timestamp: Long): NextWsState = {
    logger.debug(s"WebSocket crashed by the server while in $stateName state", t)
    handleWebSocketCheckCrash(session, next, None, t.rootMessage)
  }

  private def logCheckResult(sessionWithCheckUpdate: Session, end: Long, status: Status, code: Option[String], reason: Option[String]): Session =
    if (currentCheck.isSilent) {
      sessionWithCheckUpdate
    } else {
      logResponse(sessionWithCheckUpdate, currentCheck.resolvedName, checkSequenceStart, end, status, code, reason)
    }

  private def tryApplyingChecks[T](message: T, timestamp: Long, matchConditions: List[Check[T]], checks: List[Check[T]]): NextWsState = {
    // cache is used for both matching and checking
    val preparedCache = Check.newPreparedCache

    // if matchConditions isEmpty, all messages are considered to be matching
    val messageMatches = matchConditions.forall {
      _.check(message, session, preparedCache) match {
        case _: Success[_] => true
        case _             => false
      }
    }
    if (messageMatches) {
      logger.debug(s"Received matching message $message")
      fsm.wsLogger.logCheck(actionName, session, OK, None, Some(currentCheck.resolvedName), requestMessage)
      // matching message, apply checks
      val (sessionWithCheckUpdate, checkError) = Check.check(message, session, checks, preparedCache)

      checkError match {
        case Some(Failure(errorMessage)) =>
          logger.debug("Check failure")
          cancelTimeout()
          val newSession = logCheckResult(sessionWithCheckUpdate, timestamp, KO, None, Some(errorMessage))

          val nextAction = next match {
            case Left(n) =>
              logger.debug("Check failed, performing next action")
              fsm.wsLogger.logCheck(
                actionName,
                session,
                KO,
                Some(s"Check failed: $errorMessage"),
                Some(currentCheck.resolvedName),
                requestMessage
              )
              n
            case Right(sendMessage) =>
              // failed to reconnect, logging crash
              logger.debug("Check failed while trying to reconnect, failing pending send message and performing next action")
              fsm.wsLogger.logCheck(
                actionName,
                session,
                KO,
                Some("Check failed while trying to reconnect"),
                Some(currentCheck.resolvedName),
                requestMessage
              )
              statsEngine.logRequestCrash(session.scenario, session.groups, sendMessage.actionName, s"Couldn't reconnect: $errorMessage")
              sendMessage.next
          }

          NextWsState(
            new WsIdleState(fsm, newSession, webSocket, remainingReconnects),
            () => nextAction ! newSession
          )

        case _ =>
          logger.debug("Current check success")
          // check success
          val newSession = logCheckResult(sessionWithCheckUpdate, timestamp, OK, None, None)
          remainingChecks match {
            case nextCheck :: nextRemainingChecks =>
              // perform next check
              logger.debug("Perform next check of current check sequence")
              // [e]
              //
              // [e]
              NextWsState(this.copy(currentCheck = nextCheck, remainingChecks = nextRemainingChecks, session = newSession))

            case _ =>
              logger.debug("Current check sequence complete")
              cancelTimeout()
              remainingCheckSequences match {
                case WsFrameCheckSequence(timeout, nextCheck :: nextRemainingChecks) :: nextRemainingCheckSequences =>
                  logger.debug("Perform next check sequence")
                  // perform next CheckSequence
                  scheduleTimeout(timeout)
                  // [e]
                  //
                  // [e]
                  NextWsState(
                    this.copy(
                      currentCheck = nextCheck,
                      remainingChecks = nextRemainingChecks,
                      checkSequenceStart = timestamp,
                      remainingCheckSequences = nextRemainingCheckSequences,
                      session = newSession
                    )
                  )

                case _ =>
                  // all check sequences complete
                  logger.debug("Check sequences completed successfully")
                  val nextStateAction =
                    next match {
                      case Left(nextAction) => () => nextAction ! newSession
                      case Right(sendFrame) => () => sendFrameNextAction(newSession, sendFrame)
                    }
                  NextWsState(
                    new WsIdleState(fsm, newSession, webSocket, remainingReconnects),
                    nextStateAction
                  )
              }
          }
      }
    } else {
      logger.debug(s"Received non-matching message $message")
      fsm.wsLogger.logCheck(actionName, session, OK, None, Some(currentCheck.resolvedName), requestMessage)
      // server unmatched message, just log
      logUnmatchedServerMessage(session)
      NextWsState(this)
    }
  }

  private def handleWebSocketCheckCrash(
      session: Session,
      next: Either[Action, SendFrame],
      code: Option[String],
      errorMessage: String
  ): NextWsState = {
    cancelTimeout()
    val fullMessage = s"WebSocket crashed while waiting for check: $errorMessage"

    val newSession = logCheckResult(session, clock.nowMillis, KO, code, Some(fullMessage))
    val nextAction = next match {
      case Left(n) =>
        // failed to connect
        logger.debug("WebSocket crashed, performing next action")
        fsm.wsLogger.logCheck(
          actionName,
          session,
          KO,
          Some(s"WebSocket crashed: $errorMessage"),
          Some(currentCheck.resolvedName),
          requestMessage
        )
        n
      case Right(sendTextMessage) =>
        // failed to reconnect, logging crash
        logger.debug("WebSocket crashed while trying to reconnect, failing pending send message and performing next action")
        fsm.wsLogger.logCheck(
          actionName,
          session,
          KO,
          Some("WebSocket crashed while trying to reconnect"),
          Some(currentCheck.resolvedName),
          requestMessage
        )
        statsEngine.logRequestCrash(session.scenario, session.groups, sendTextMessage.actionName, s"Couldn't reconnect: $errorMessage")
        sendTextMessage.next
    }

    NextWsState(
      new WsCrashedState(fsm, Some(errorMessage), remainingReconnects),
      () => nextAction ! newSession
    )
  }
}
