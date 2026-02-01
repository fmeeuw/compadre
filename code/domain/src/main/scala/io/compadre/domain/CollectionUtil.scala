package io.compadre.domain

object CollectionUtil {

  import scala.collection.Factory

  def traverse[A, CC[A] <: collection.Iterable[A], E, B](
      xs: CC[A]
  )(f: A => Either[E, B])(implicit cbf: Factory[B, CC[B]]): Either[E, CC[B]] = {
    val builder = cbf.newBuilder
    val i = xs.iterator
    while (i.hasNext) f(i.next) match {
      case Right(b) => builder += b
      case Left(e)  => return Left(e)
    }
    Right(builder.result)
  }

}
