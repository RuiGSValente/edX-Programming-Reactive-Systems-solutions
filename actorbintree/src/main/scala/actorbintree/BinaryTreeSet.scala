/**
  * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
  */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot
  var newRoot = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case operation: Operation => root ! operation

    case GC => {
      root ! CopyTo(newRoot)
      garbageCollecting(newRoot)
    }
  }
  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {

    case operation: Operation => pendingQueue.enqueue(operation)

    case CopyFinished => {

      root ! PoisonPill
      root = newRoot
      newRoot ! PoisonPill

      pendingQueue.foreach(root ! _)
      pendingQueue = Queue.empty

      context.become(normal)
    }
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {

    case Contains(requester,id,newElem)=>

      if(elem == newElem)
        requester ! ContainsResult(id,!removed)

      else if (subtrees.contains(childPos(newElem))) {
        subtrees(childPos(newElem)) ! Contains(requester, id, newElem)
      }
      else requester ! ContainsResult(id,false)


    case Insert(requester,id,newElem)=>
      if(elem == newElem){
        if(removed) removed = false
        requester ! OperationFinished(id)
      }
      else if (subtrees.contains(childPos(newElem))) {
        subtrees(childPos(newElem)) ! Insert(requester, id, newElem)
      }
      else {
        subtrees += (childPos(newElem) -> context.actorOf(BinaryTreeNode.props(newElem, initiallyRemoved = false)))
        requester ! OperationFinished(id)
      }

    case Remove(requester,id,newElem)=>
      if(elem == newElem && !removed){
        removed = true
        requester ! OperationFinished(id)
      }
      else  if (!subtrees.contains(childPos(newElem)))
        requester ! OperationFinished(id)
      else subtrees(childPos(newElem)) ! Remove(requester, id, newElem)

  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = ???

  def childPos(newElem: Int ) = if(elem > newElem) Left else Right

}
