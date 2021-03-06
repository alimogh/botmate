// Copyright (c) 2012-2016 Saarland University
// All rights reserved.
//
// Author: Konrad Jamrozik, konrad.jamrozik@cispa.saarland
//
// This file is part of the "BotMate" project.
//
// github.com/konrad-jamrozik/botmate

package com.konradjamrozik.botmate.demo

interface IAdb {
  fun tap(x: Int, y: Int)

  fun pressHome()

  fun press(androidButton: AndroidButton)

  fun devices(): List<String>
}

