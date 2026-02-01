package io.compadre.domain

case class FieldPath(segments: List[String]) {

  def /(name: String): FieldPath = FieldPath(segments :+ name)

  def isRoot: Boolean = segments.isEmpty

  def parent: FieldPath = FieldPath(segments.dropRight(1))

  def name: Option[String] = segments.lastOption

  override def toString: String =
    if (segments.isEmpty) "/" else segments.mkString("/")
}

object FieldPath {
  val Root: FieldPath = FieldPath(List.empty)

  def apply(path: String): FieldPath =
    if (path.isEmpty || path == "/") Root
    else FieldPath(path.split("/").filter(_.nonEmpty).toList)
}
