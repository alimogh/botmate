// Copyright (c) 2012-2016 Saarland University
// All rights reserved.
//
// Author: Konrad Jamrozik, konrad.jamrozik@cispa.saarland
//
// This file is part of the "BotMate" project.
//
// github.com/konrad-jamrozik/botmate

package com.konradjamrozik.botmate.controller

import com.google.common.collect.Lists
import groovy.util.logging.Slf4j

@Slf4j
public class RobotController implements IRobotController
{

  public static final String CEBIT_IMAC_EXPECTED_SERIAL_PORT = "/dev/tty.usbmodem1.0.1";
  public static final String WINDOWS_EXPECTED_SERIAL_PORT = "COM7";
  private RobotConfiguration robotConfig;
  private Reader userInputReader;
  private ISerialDriver serialDriver;
  private ICoordinateMapper coordinateMapper;
  private IRobotPathPlotter robotPathPlotter;
  private float currentX;
  private float currentY;
  private final double slowdownLength = 40;
  private final double speedupLength = 40;

  RobotController(RobotConfiguration robotConfig, Reader userInputReader, ISerialDriver serialDriver,
                  ICoordinateMapper coordinateMapper, IRobotPathPlotter robotPathPlotter)
  {
    this.robotConfig = robotConfig;

    this.userInputReader = userInputReader;

    this.serialDriver = serialDriver;
    this.coordinateMapper = coordinateMapper;
    this.robotPathPlotter = robotPathPlotter;
  }

  @Override
  public boolean connect(String serialPortName) throws Exception
  {
    serialDriver.connect(serialPortName)
  }

  @Override
  public boolean interactiveConnect() throws RobotException
  {
    log.info("Connecting to the robot... (please wait while serial ports are retrieved)");

    Vector<String> serialPortNames = serialDriver.getSerialPortNames();
    String chosenSerialPortName = chooseSerialPort(serialPortNames);

    if (chosenSerialPortName == null)
      return false;

    try
    {
      serialDriver.connect(chosenSerialPortName)
    } catch (Exception e)
    {
      e.printStackTrace()
    };

    log.info("DONE connecting to the robot. Connected on serial port: {}", chosenSerialPortName);

    return true;
  }


  @Override
  public void calibrate() throws RobotException
  {
    log.debug("Calibrating the robot...");
    serialDriver.send("G28 X0 Y0");
    serialDriver.receive();
    currentX = 0;
    currentY = 0;
    log.debug("DONE calibrating the robot.");

  }

  @Override
  public void moveToMinXY(boolean isLandscapeOrientation) throws RobotException
  {
    log.trace("Moving robot to the start corner.");
    moveToMappedCoordinates(robotConfig.robotMinX, robotConfig.robotMinY, robotConfig.robotSpeedSlow,
      isLandscapeOrientation);
  }

  @Override
  public void moveToMaxXY(boolean isLandscapeOrientation) throws RobotException
  {
    log.trace("Moving robot to the end corner.");
    moveToMappedCoordinates(robotConfig.robotMaxX, robotConfig.robotMaxY, robotConfig.robotSpeedSlow,
      isLandscapeOrientation);
  }

  // Design note: ideally, the distinction between landscape and portrait back button locations should happen 
  // in ICoordinateMapper, not in IRobotController.
  @Override
  public void moveToBackButton(boolean isLandscapeOrientation) throws RobotException
  {
    log.trace("Moving robot to the back button, isLandscapeOrientation = {}.", isLandscapeOrientation);
    if (isLandscapeOrientation)
      _moveToMappedCoordinates(robotConfig.robotBackButtonLandscapeX, robotConfig.robotBackButtonLandscapeY,
        true /* landscapeOrientation */);
    else
      _moveToMappedCoordinates(robotConfig.robotBackButtonPortraitX, robotConfig.robotBackButtonPortraitY,
        false /* false = portraitOrientation */);
  }

  @Override
  public void moveToCoordinates(int x, int y, int speed, boolean isLandscapeOrientation) throws RobotException
  {
    log.trace("Moving robot to raw coordinates {} {}.", x, y);

    float mappedX = coordinateMapper.mapToX(x, y, isLandscapeOrientation);
    float mappedY = coordinateMapper.mapToY(x, y, isLandscapeOrientation);

    moveToMappedCoordinates(mappedX, mappedY, speed, isLandscapeOrientation);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void moveToMappedCoordinates(float mappedX, float mappedY, int speed, boolean isLandscapeOrientation)
    throws RobotException
  {
    log.trace("Moving robot to mapped coordinates {} {}.", mappedX, mappedY);

    float mappedStartX = currentX;
    float mappedStartY = currentY;
    float mappedEndX = mappedX;
    float mappedEndY = mappedY;

    List<Pair<Float, Float>> pathPoints;

    if (speed == robotConfig.robotSpeedSlow)
    {
      log.trace("Detected slow robot speed of {}. Moving over straight line.", robotConfig.robotSpeedSlow);
      pathPoints = Lists.newArrayList(new Pair<Float, Float>(mappedX, mappedY));
    } else
      pathPoints = robotPathPlotter.plot(mappedStartX, mappedStartY, mappedEndX, mappedEndY);

    log.trace("Moving over an arc. Start (x,y) = ({},{}). End (x,y) = ({},{}) Length = {}", mappedStartX, mappedStartY,
      mappedEndX,
      mappedEndY, pathPoints.size());

    moveThroughPath(speed, pathPoints);

    serialDriver.send(String.format("G4 P1")); // send OK when robot finishes moving.
    serialDriver.receive();  // wait for OK that will be sent when robot finishes moving.
  }

  private void moveThroughPath(int speed, List<Pair<Float, Float>> pathPoints) throws RobotException
  {

    int slowDownPoints = 0;
    int speedupPoints = 0;
    double lastPlottedArcLength = 0;
    if (pathPoints.size() > 1 && robotPathPlotter instanceof RobotPathPlotterArc)
    {
      RobotPathPlotterArc arcPlotter = (RobotPathPlotterArc) robotPathPlotter;
      lastPlottedArcLength = arcPlotter.lastPlottedArcLength;
      double segmentLength = arcPlotter.lastPlottedArcLength / pathPoints.size();
      log.trace("last plotted arc length: {}", arcPlotter.lastPlottedArcLength);
      slowDownPoints = (int) (slowdownLength / segmentLength);
      speedupPoints = (int) (speedupLength / segmentLength);
      log.trace("slow down points: {}. speedup points: {} allPoints: {}", slowDownPoints, speedupPoints,
        pathPoints.size());
    }


    log.trace("Last plotted arc length: {}", lastPlottedArcLength);
    if (lastPlottedArcLength > 1 && lastPlottedArcLength <= 40)
    {
      log.trace("Moving in slow motion of {}", robotConfig.robotSpeedSlow);
      Pair<Float, Float> point = pathPoints.get(pathPoints.size() - 1);
      serialDriver.send(String.format(
        "G1 X%.1f Y%.1f F%d",
        point.getX(),
        point.getY(), robotConfig.robotSpeedSlow
      ));

      serialDriver.receive();
      currentX = point.getX();
      currentY = point.getY();

    } else
    {
      int pointCounter = 0;
      for (Pair<Float, Float> point : pathPoints)
      {
        pointCounter++;
        serialDriver.send(String.format(
          "G1 X%.1f Y%.1f F%d",
          point.getX(),
          point.getY(),
          slowDownPoints == 0 ? speed : computeSpeed(speed, pointCounter, pathPoints.size(), slowDownPoints,
            speedupPoints)));

        serialDriver.receive();
        currentX = point.getX();
        currentY = point.getY();

      }
    }

    log.trace("Finished moving over an arc.");
  }
  
  private static int computeSpeed(int speed, int pointCounter, int pointCount, int slowDownPoints, int speedupPoints)
  {
    if (speedupPoints + slowDownPoints >= pointCount)
    {
      speedupPoints = pointCount / 2;
      slowDownPoints = pointCount - speedupPoints;

    }

    if (pointCounter <= speedupPoints)
      return (int) (speed * (pointCounter / (float) speedupPoints));

    if (pointCounter <= pointCount - slowDownPoints)
      return speed;

    pointCounter -= pointCount - slowDownPoints;
    pointCount -= pointCount - slowDownPoints;

    return (int) (speed * ((pointCount - (pointCounter - 1)) / (float) pointCount));
  }


  @Override
  public void moveDown() throws RobotException
  {
    log.trace("Moving robot down.");
    serialDriver.send(String.format("G1 Z%d F%d", robotConfig.robotLoweredZ, robotConfig.robotSpeed));
    serialDriver.receive();
  }

  @Override
  public void moveUp() throws RobotException
  {
    log.trace("Moving robot up.");
    serialDriver.send(String.format("G1 Z%d F%d", robotConfig.robotLiftedZ, robotConfig.robotSpeed));
    serialDriver.receive();

  }

  @Override
  public void disconnect()
  {
    serialDriver.close();

  }

  @Override
  public void runRaw(String command) throws RobotException
  {
    serialDriver.send(command);
    if (robotConfig.echoCable)
      serialDriver.receive(command);
    else
      serialDriver.receive();

  }

  private String chooseSerialPort(Vector<String> serialPortNames)
  {
    String chosenSerialPortName;

    if (serialPortNames.contains(WINDOWS_EXPECTED_SERIAL_PORT))
    {
      log.info("Found port {}, expected to be found on Windows machine with robot connected. " +
        "Connecting to it.", WINDOWS_EXPECTED_SERIAL_PORT);
      chosenSerialPortName = WINDOWS_EXPECTED_SERIAL_PORT;

    } else if (serialPortNames.contains(CEBIT_IMAC_EXPECTED_SERIAL_PORT))
    {
      log.info("Found port {}, expected to be found on CeBIT iMac with robot connected. " +
        "Connecting to it.", CEBIT_IMAC_EXPECTED_SERIAL_PORT);
      chosenSerialPortName = CEBIT_IMAC_EXPECTED_SERIAL_PORT;
    } else
    {
      log.info("Failed to find ports for automatic connection: {} (windows) or {} (CeBIT iMac)",
        WINDOWS_EXPECTED_SERIAL_PORT, CEBIT_IMAC_EXPECTED_SERIAL_PORT);
      log.info("Found following ports: ");
      for (int i = 0; i < serialPortNames.size(); i++)
        log.info("{}: {}", i + 1, serialPortNames.get(i));
      log.info("Choose the serial port you wish to connect to or 0 to abort and terminate DroidMate.");
      String userInput = userInputReader.readLine()
      int userInputPortNumber = Integer.parseInt(userInput);

      if (userInputPortNumber == 0)
      {
        log.info("Did not connect to serial port due to user-chosen abort.");
        return null;
      }

      chosenSerialPortName = serialPortNames.get(userInputPortNumber - 1);

    }
    log.debug("Chosen serial port: {}", chosenSerialPortName);
    return chosenSerialPortName;
  }

  private void _moveToMappedCoordinates(int x, int y, boolean isLandscapeOrientation) throws RobotException
  {
    moveToMappedCoordinates(x, y, robotConfig.robotSpeed, isLandscapeOrientation);
  }


}
