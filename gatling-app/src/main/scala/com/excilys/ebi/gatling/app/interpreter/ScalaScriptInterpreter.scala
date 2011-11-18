/**
 * Copyright 2011 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.excilys.ebi.gatling.app.interpreter

import java.io.{ StringWriter, PrintWriter, File }

import scala.tools.nsc.interpreter.AbstractFileClassLoader
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{ Settings, Global }

import org.joda.time.DateTime

import com.excilys.ebi.gatling.core.config.GatlingConfig.CONFIG_SIMULATION_SCALA_PACKAGE
import com.excilys.ebi.gatling.core.util.PathHelper.GATLING_SCENARIOS_FOLDER
import com.excilys.ebi.gatling.core.util.ReflectionHelper.getNewInstanceByClassName

/**
 * This class is used to interpret scala simulations
 */
class ScalaScriptInterpreter extends Interpreter {

	val byteCodeDir = new VirtualDirectory("memory", None)
	val classLoader = new AbstractFileClassLoader(byteCodeDir, this.getClass.getClassLoader)

	/**
	 * This method launches the interpretation of the simulation and runs it
	 *
	 * @param fileName the name of the file containing the simulation description
	 * @param startDate the date at which the launch was asked
	 */
	def run(fileName: String, startDate: DateTime) {
		compile(new File(GATLING_SCENARIOS_FOLDER, fileName))
		val runner = getNewInstanceByClassName[App](CONFIG_SIMULATION_SCALA_PACKAGE + "Simulation", classLoader)
		runner.main(Array(startDate.toString));
	}

	/**
	 * Compiles all the files needed for the simulation
	 *
	 * @param sourceDirectory the file containing the simulation description
	 */
	def compile(sourceDirectory: File): Unit = {
		// Prepare an object for collecting error messages from the compiler
		val messageCollector = new StringWriter
		val messageCollectorWrapper = new PrintWriter(messageCollector)

		// Initialize the compiler
		val settings = generateSettings
		val reporter = new ConsoleReporter(settings, Console.in, messageCollectorWrapper)
		val compiler = new Global(settings, reporter)

		// Attempt compilation
		val files =
			if (sourceDirectory.isFile)
				sourceDirectory :: findFiles(new File(sourceDirectory.getAbsolutePath.substring(0, sourceDirectory.getAbsolutePath.lastIndexOf("@"))))
			else
				findFiles(sourceDirectory)

		(new compiler.Run).compile(files.map(_.toString))

		// Bail out if compilation failed
		if (reporter.hasErrors) {
			reporter.printSummary
			messageCollectorWrapper.close
			throw new RuntimeException("Compilation failed:\n" + messageCollector.toString)
		}
	}

	/**
	 * Finds all the scala files under root
	 *
	 * @param root the root file under which the files should be found
	 */
	private def findFiles(root: File): List[File] = {
		if (root.isFile)
			List(root)
		else
			makeList(root.listFiles).flatMap { f => findFiles(f) }
	}

	/**
	 * Makes a list from an array
	 *
	 * @param a the array to be converted
	 */
	private def makeList(a: Array[File]): List[File] = {
		if (a == null)
			Nil
		else
			a.toList
	}

	/**
	 * Generates the settings of the scala compiler
	 */
	private def generateSettings: Settings = {
		val settings = new Settings
		settings.usejavacp.value = true
		settings.outputDirs.setSingleOutput(byteCodeDir)
		settings.deprecation.value = true
		settings.unchecked.value = true
		settings
	}
}