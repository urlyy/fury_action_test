/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fury.logging;

import static org.apache.fury.logging.LogLevel.ERROR_LEVEL;
import static org.apache.fury.logging.LogLevel.INFO_LEVEL;
import static org.apache.fury.logging.LogLevel.WARN_LEVEL;

import org.slf4j.spi.LocationAwareLogger;

/** A logger to forward logging to slf4j. */
public class Slf4jLogger implements Logger {
  private static final String FQCN = Slf4jLogger.class.getName();

  private final boolean isLocationAwareLogger;

  private final org.slf4j.Logger logger;

  public Slf4jLogger(Class<?> cls) {
    this.logger = org.slf4j.LoggerFactory.getLogger(cls);
    this.isLocationAwareLogger = logger instanceof LocationAwareLogger;
  }

  @Override
  public void info(String msg) {
    info(msg, (Object[]) null);
  }

  @Override
  public void info(String msg, Object arg) {
    info(msg, new Object[] {arg});
  }

  @Override
  public void info(String msg, Object arg1, Object arg2) {
    info(msg, new Object[] {arg1, arg2});
  }

  @Override
  public void info(String msg, Object... args) {
    if (LoggerFactory.getLogLevel() < INFO_LEVEL) {
      return;
    }
    if (isLocationAwareLogger) {
      ((LocationAwareLogger) logger).log(null, FQCN, LocationAwareLogger.INFO_INT, msg, args, null);
    } else {
      logger.info(msg, args);
    }
  }

  @Override
  public void warn(String msg) {
    warn(msg, (Object[]) null);
  }

  @Override
  public void warn(String msg, Object arg) {
    warn(msg, new Object[] {arg});
  }

  @Override
  public void warn(String msg, Object arg1, Object arg2) {
    warn(msg, new Object[] {arg1, arg2});
  }

  @Override
  public void warn(String msg, Object... args) {
    if (LoggerFactory.getLogLevel() < WARN_LEVEL) {
      return;
    }
    if (isLocationAwareLogger) {
      ((LocationAwareLogger) logger).log(null, FQCN, LocationAwareLogger.WARN_INT, msg, args, null);
    } else {
      logger.warn(msg, args);
    }
  }

  @Override
  public void warn(String msg, Throwable t) {
    if (LoggerFactory.getLogLevel() < WARN_LEVEL) {
      return;
    }
    if (isLocationAwareLogger) {
      ((LocationAwareLogger) logger).log(null, FQCN, LocationAwareLogger.WARN_INT, msg, null, t);
    } else {
      logger.warn(msg, t);
    }
  }

  @Override
  public void error(String msg) {
    error(msg, (Object[]) null);
  }

  @Override
  public void error(String msg, Object arg) {
    error(msg, new Object[] {arg});
  }

  @Override
  public void error(String msg, Object arg1, Object arg2) {
    error(msg, new Object[] {arg1, arg2});
  }

  @Override
  public void error(String msg, Object... args) {
    if (LoggerFactory.getLogLevel() < ERROR_LEVEL) {
      return;
    }
    if (isLocationAwareLogger) {
      ((LocationAwareLogger) logger)
          .log(null, FQCN, LocationAwareLogger.ERROR_INT, msg, args, null);
    } else {
      logger.error(msg, args);
    }
  }

  @Override
  public void error(String msg, Throwable t) {
    if (LoggerFactory.getLogLevel() < ERROR_LEVEL) {
      return;
    }
    if (isLocationAwareLogger) {
      ((LocationAwareLogger) logger).log(null, FQCN, LocationAwareLogger.ERROR_INT, msg, null, t);
    } else {
      logger.error(msg, t);
    }
  }
}
