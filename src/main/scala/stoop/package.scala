import dispatch._
import scalaz._
import scalaz.Free.{freeMonad => _, _}
import scalaz.effect._
import annotation.tailrec
import spray.json._
import DefaultJsonProtocol._

package object stoop {

  type Bytes = Array[Byte] // historical scalaz-streams reasons

  /** Abstract algebra for manipulating a CouchDB */
  type Couch[A] = Free[CouchF, A]

  /** A couch action that has no effect and results in the given value. */
  def just[A](a: => A): Couch[A] = Monad[Couch].pure(a)

  /** A couch action that fails. */
  def noop[A]: Couch[A] = liftF(Fail(new Exception("The action had no result.")):CouchF[A])

  /** A couch action that has no effect and no result. */
  def commit: Couch[Unit] = just(())

  /** A couch action that always fails with the given error. */
  def error[A](e: Throwable) = liftF(Fail(e):CouchF[A])

  implicit class CouchOps[A](c: Couch[A]) {
    def apply[B](k: A => Couch[B]): Couch[B] = c flatMap k
    def orElse(n: => Couch[A]): Couch[A] = c.resume match {
      case -\/(Fail(_)) => n
      case -\/(s)    => liftF(s).flatMap(_ orElse n)
      case \/-(x)    => just(x)
    }
  }

  implicit val couchMonad: MonadPlus[Couch] = new MonadPlus[Couch] {
    def point[A](a: => A) = Free.point(a)
    def bind[A,B](ma: Couch[A])(f: A => Couch[B]) = ma flatMap f
    def plus[A](m: Couch[A], n: => Couch[A]) = m orElse n
    def empty[A] = noop
  }

  def isDBName(s: String): Boolean =
    s matches "[a-z_][0-9a-z_$()+-/]*"

  /** Create a new database with the given name. */
  def createDB(name: String): Couch[DB] =
    liftF(CreateDB(name, DB(name)))

  /** Drop the database with the given name. */
  def dropDB(name: String): Couch[Boolean] =
    liftF(DropDB(name, identity))

  /** Get all existing databases in a list. */
  def getAllDBs: Couch[List[DB]] =
    liftF(GetAllDBs(identity))

}

