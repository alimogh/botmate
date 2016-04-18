// Copyright (c) 2012-2016 Saarland University
// All rights reserved.
//
// Author: Konrad Jamrozik, konrad.jamrozik@cispa.saarland
//
// This file is part of the "BotMate" project.
//
// github.com/konrad-jamrozik/botmate

package com.github.konrad_jamrozik.botmate.demo

import com.github.konrad_jamrozik.botmate.controller.*
import java.io.InputStreamReader
import kotlin.Pair

class RobotControllerAdapter : IRobot
{
  private val robotConfig: RobotConfiguration by lazy { RobotConfiguration() }

  private val robotController: IRobotController by lazy { buildRobotController() }

  private fun buildRobotController() : IRobotController {

    val userInputReader = InputStreamReader(System.`in`, Charsets.UTF_8)
    val robotController = RobotController(
      robotConfig,
      userInputReader,
      SerialDriver(robotConfig),
      // KJA2 decouple Nexus10. Move to Nexus10Buttons or related
      CoordinateMapperNexus10(robotConfig),
      RobotPathPlotterArc(robotConfig)
    )
    return robotController
  }

  override fun connectAndCalibrate() {
    check(robotController.connect(serialPortName)) {"check failed: robotController.connect($serialPortName)"}
    robotController.calibrate()
  }

  override fun moveTo(coordinates: Pair<Int, Int>) {

    robotController.moveToCoordinates(
      coordinates.first,
      coordinates.second,
      robotConfig.robotSpeed,
      /* isLandscapeOrientation */ true)
  }

  override fun moveDown() {
    robotController.moveDown()
  }

  override fun moveUp() {
    robotController.moveUp()
  }

  override fun moveToLowerRightCorner() {
    // KJA2 unhardcode coordinates
    robotController.moveToMaxXY(/* isLandscapeOrientation: */ true);
  }
  
  override fun disconnect() {
    robotController.disconnect()
  }

  companion object {
    val serialPortEnvVar = "BOTMATE_ROBOT_SERIAL_PORT"

    val serialPortName = System.getenv(serialPortEnvVar) ?:
      "undefined. Please set environmental variable $serialPortEnvVar"
  }
}
