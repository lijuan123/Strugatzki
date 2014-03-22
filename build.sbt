import AssemblyKeys._

name               := "Strugatzki"

version            := "2.4.0-SNAPSHOT"

organization       := "de.sciss"

scalaVersion       := "2.11.0-RC3"

crossScalaVersions := Seq("2.11.0-RC3", "2.10.4")

description        := "Algorithms for extracting audio features and matching audio file similarities"

homepage           := Some(url("https://github.com/Sciss/" + name.value))

licenses           := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

libraryDependencies ++= Seq(
  "de.sciss"          %% "processor"        % "0.2.+",    // provides futures with progess report and cancel
  "de.sciss"          %% "scalacollider"    % "1.11.+",   // for the feature ugens
  "de.sciss"          %% "span"             % "1.2.+",    // representation of time spans
  "de.sciss"          %  "intensitypalette" % "1.0.0",    // color scheme for self similarity
  "de.sciss"          %% "fileutil"         % "1.1.+",    // easy path compositions
  "com.github.scopt"  %% "scopt"            % "3.2.0",    // parsing command line options
  "org.scalatest"     %% "scalatest"        % "2.1.2" % "test"
)

// retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

// ---- build info ----

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
  BuildInfoKey.map(homepage) { case (k, opt)             => k -> opt.get },
  BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
)

buildInfoPackage := "de.sciss.strugatzki"

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (version.value endsWith "-SNAPSHOT")
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
}

// ---- packaging ----

seq(assemblySettings: _*)

test    in assembly := ()

target  in assembly := baseDirectory.value

jarName in assembly := s"${name.value}.jar"

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("music-information-retrieval", "machine-learning", "music", "dsp", "feature-extraction")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

