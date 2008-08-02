package com.twitter

import com.twitter.tomservo._
import org.specs.runner.SpecsFileRunner


object TestRunner extends SpecsFileRunner("src/test/scala/**/*.scala", ".*")
