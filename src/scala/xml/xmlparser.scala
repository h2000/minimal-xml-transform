/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala.xml
import Utility.sbToString
import annotation.tailrec


//import Utility.{ isNameStart }

case class NamespaceBindingS(override val prefix: String, override val uri: String, override val parent: NamespaceBinding, space: String = " ") extends NamespaceBinding(prefix, uri, parent) { 
  override def getURI(_prefix: String): String =
    if (prefix == _prefix) uri else {
      if (parent == null)
        { if (_prefix == "xml") "http://www.w3.org/XML/1998/namespace" else null: String }
      else
        parent getURI _prefix
    }
}
// Need an analogous TopScopeS
case object TopScopeS extends NamespaceBindingS(null, null, null, "") { 
  import XML.{ xml, namespace }
  
  override def getURI(prefix1: String): String =
    if (prefix1 == xml) namespace else null

  override def getPrefix(uri1: String): String =
    if (uri1 == namespace) xml else null

  override def toString() = ""
  override def buildString(stop: NamespaceBinding) = ""
  override def buildString(sb: StringBuilder, ignore: NamespaceBinding) = {}
}

/** Essentially, every method in here is a facade, delegating to next.
 *  It provides a backstop for the unusual collection defined by MetaData,
 *  sort of a linked list of tails.
 */
case class WhiteSpace(space: String, override val next: MetaData) extends MetaData {    
  def this(space: String, next: WhiteSpace) = this(next.space, next.next)
  def copy(next: MetaData) = WhiteSpace(space, next)
  def getNamespace(owner: Node) = null

  def key = null
  def value = null
  def isPrefixed = false

  override def length = next.length(1)
  override def length(i: Int) = next.length(i + 1)
  
  override def strict_==(other: Equality) = other match {
    case x: WhiteSpace  => x.space == space && x.next == next
    case _              => false
  }


  /** forwards the call to next (because caller looks for unprefixed attribute */
  def apply(key: String): Seq[Node] = next(key)

  /** gets attribute value of qualified (prefixed) attribute with given key 
   */
  def apply(namespace: String, scope: NamespaceBinding, key: String): Seq[Node] = next(namespace, scope, key)

  def toString1(sb: StringBuilder): Unit = sb append toString1
  override def toString1(): String = space
  override def toString(): String = { val buf = new StringBuilder; toString1(buf); buf append next.toString; buf.toString }

  //override def buildString(sb: StringBuilder): StringBuilder = sb append space
  override def wellformed(scope: NamespaceBinding) = next.wellformed(scope)

  def remove(key: String) = WhiteSpace(space, next.remove(key))
  def remove(namespace: String, scope: NamespaceBinding, key: String) = WhiteSpace(space, next.remove(namespace, scope, key))
}

object WhiteSpace {
  def apply(space: String, next: WhiteSpace) = next
}

//Unroll what should be a duck punch for RTTI reasons; replaces attributes used for xmlns
class fakePrefixedAttribute(
  override val pre: String,
  override val key: String,
  override val value: Seq[Node],
  override val next: MetaData) extends PrefixedAttribute(pre, key, value, next) {
  override def wellformed(scope: NamespaceBinding): Boolean = {
    (next wellformed scope)
  }
  override def toString1(sb: StringBuilder): Unit = { }
  override def toString1(): String = ""
}
class fakeUnprefixedAttribute(
  override val key: String,
  override val value: Seq[Node],
  next1: MetaData) extends UnprefixedAttribute(key, value, next1) {
  override def wellformed(scope: NamespaceBinding): Boolean = {
    (next wellformed scope)
  }
  override def toString1(sb: StringBuilder): Unit = { }
  override def toString1(): String = ""
}

trait whitespaceRootPatch {
  self: MetaData =>
  /**
   * Duck punch the official version of this to take into account WhiteSpace.
   */
  def normalize(attribs: MetaData, scope: NamespaceBinding): MetaData = {    
    def iterate(md: MetaData, normalized_attribs: MetaData, set: Set[String]): MetaData = {
      lazy val key = getUniversalKey(md, scope)
      if (md eq Null) normalized_attribs
      else if (key == null || set(key)) iterate(md.next, normalized_attribs, set)
      else iterate(md.next, md copy normalized_attribs, set + key)
    }
    iterate(attribs, Null, Set())
  }
  /**
   * returns key if md is unprefixed, pre+key is md is prefixed
   */
  def getUniversalKey(attrib: MetaData, scope: NamespaceBinding) = attrib match {
    case prefixed: PrefixedAttribute     => scope.getURI(prefixed.pre) + prefixed.key
    case unprefixed: UnprefixedAttribute => unprefixed.key
    case _ => null
  }
  /** 
   * appends all attributes from new_tail to attribs, without attempting to detect
   * or remove duplicates. The method guarantees that all attributes from attribs come before
   * the attributes in new_tail, but does not guarantee to preserve the relative order of attribs.
   * Duplicates can be removed with normalize.
   */
  @tailrec
  private def concatenate(attribs: MetaData, new_tail: MetaData): MetaData =
    if (attribs eq Null) new_tail
    else concatenate(attribs.next, attribs copy new_tail)
  
  /**
   *  returns MetaData with attributes updated from given MetaData
   */
  def update(attribs: MetaData, scope: NamespaceBinding, updates: MetaData): MetaData =
    normalize(concatenate(updates, attribs), scope)
  override def append(updates: MetaData, scope: NamespaceBinding = TopScope): MetaData =
    update(this, scope, updates)
}
