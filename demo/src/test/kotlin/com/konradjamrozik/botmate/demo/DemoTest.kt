// Copyright (c) 2012-2016 Saarland University
// All rights reserved.
//
// Author: Konrad Jamrozik, konrad.jamrozik@cispa.saarland
//
// This file is part of the "BotMate" project.
//
// github.com/konrad-jamrozik/botmate

package com.konradjamrozik.botmate.demo

import org.junit.Test

class DemoTest {

  @Test
  fun runs_demo_with_device_stub_and_robot_stub() = Demo.withDeviceStubAndRobotStub.run()
  
  @Test
  fun runs_demo_with_robot_stub() = Demo.withRobotStub.run()

  @Test
  fun runs_demo_with_device_stub() = Demo.withDeviceStub.run()

  @Test
  fun runs_demo_full() = Demo.full.run()
}