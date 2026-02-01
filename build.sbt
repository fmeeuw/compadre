ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.compadre"
ThisBuild / version := "0.1.0-SNAPSHOT"

val root = (project in file("."))
  .aggregate(domain, compatibility, registry, codegen)

// Core schema types: operations, schema types, schema builder
lazy val domain = (project in file("code/domain"))
  .settings(
    name := "compadre-domain"
  )

// Compatibility checking: can version A read version B?
lazy val compatibility = (project in file("code/compatibility"))
  .settings(
    name := "compadre-compatibility"
  )
  .dependsOn(domain)

// Schema registry: store schemas, track producers/consumers, validation
lazy val registry = (project in file("code/registry"))
  .settings(
    name := "compadre-registry"
  )
  .dependsOn(domain, compatibility)

// Code generation: Schema -> Scala case classes
lazy val codegen = (project in file("code/codegen"))
  .settings(
    name := "compadre-codegen"
  )
  .dependsOn(domain)
