name := "PipelineDescriptionLanguage"
version := "0.0.1"
scalaVersion := "2.13.17"

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.8.0",

  // Parsing & Pretty Printing
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
  "com.lihaoyi" %% "pprint" % "0.5.6",

  // SMT Solving
  "tools.aqua" % "z3-turnkey" % "4.14.1",

  // Command Line Parsing
  "com.github.scopt" % "scopt_2.13" % "4.0.1",

  // Logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.2" % "test",
  "org.scalactic" %% "scalactic" % "3.2.2",
)

scalacOptions += "-language:implicitConversions"

//Deployment Options
assembly / assemblyJarName := "pdl.jar"
assembly / test := {}
assembly / mainClass := Some("pipedsl.Main")


// Without this I'm getting errors:
// [error] java.lang.RuntimeException: deduplicate: different file contents found in the following:
// [error] jspecify-1.0.0.jar:META-INF/versions/9/module-info.class
// [error] turnkey-support-1.0.0.jar:META-INF/versions/9/module-info.class
// [error] 4.14.1/z3-turnkey-4.14.1.jar:META-INF/versions/9/module-info.class
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) =>
    xs match {
      case "MANIFEST.MF" :: Nil => MergeStrategy.discard
      case _ => MergeStrategy.deduplicate
    }
  case x => MergeStrategy.deduplicate
}