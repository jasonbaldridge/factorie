/* Copyright (C) 2008-2010 University of Massachusetts Amherst,
   Department of Computer Science.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package cc.factorie
import cc.factorie.la._

//trait TensorDomain extends Domain[Tensor]
//object TensorDomain extends TensorDomain

/** An abstract variable whose value is a Tensor. */
trait TensorVar extends /*VarWithDomain[Tensor] with*/ Var with ValueBound[Tensor] {
  //def domain: TensorDomain
  def value: Tensor
}

/** A TensorVar with a specific Tensor type value. */
trait TypedTensorVar[+A<:Tensor] extends TensorVar with ValueBound[A] {
  override def value: A
}

// TODO Consider also, just in case needed:
// trait TypedTensorVar[+A<:Tensor] extends TensorVar with VarAndValueType[TypedTensorVar[A],A]
// trait TensorVar extends TypedTensorVar[Tensor]

trait MutableTensorVar[A<:Tensor] extends TypedTensorVar[A] with MutableVar[A] {
  //def domain: TensorDomain //with Domain[A]
  private var _value: A = null.asInstanceOf[A]
  @inline final def value: A = _value // This method will definitely not make a copy
  // Methods that track modifications on a DiffList
  def set(newValue:A)(implicit d:DiffList): Unit = {
    if (d ne null) d += SetTensorDiff(_value, newValue)
    _value = newValue
  }
  def update(index:Int, newValue:Double)(implicit d:DiffList): Unit = {
    if (d ne null) throw new Error("Not yet implemented")
    value.update(index, newValue)
  }
  def increment(index:Int, incr:Double)(implicit d:DiffList): Unit = {
    if (d ne null) d += IncrementTensorIndexDiff(index, incr)
    value.+=(index, incr)
  }
  def increment(incr:Tensor)(implicit d:DiffList): Unit = {
    require(incr.length == value.length)
    if (d ne null) d += IncrementTensorDiff(incr)
    value += incr
  }
  def zero(implicit d:DiffList): Unit = {
    if (d ne null) d += ZeroTensorDiff(value.toArray)
    value.zero()
  }
  
  case class SetTensorDiff(oldValue:A, newValue:A) extends Diff {
    def variable = MutableTensorVar.this
    def undo = _value = oldValue
    def redo = _value = newValue
  }
  case class UpdateTensorDiff(index:Int, oldValue:Double, newValue:Double) extends Diff {
    def variable = MutableTensorVar.this
    def undo = _value(index) = oldValue
    def redo = _value(index) = newValue
  }
  case class IncrementTensorIndexDiff(index:Int, incr:Double) extends Diff {
    def variable = MutableTensorVar.this
    def undo = _value.+=(index, -incr)
    def redo = _value.+=(index, incr)
  }
  case class IncrementTensorDiff(t: Tensor) extends Diff {
    def variable = MutableTensorVar.this
    def undo = _value -= t // Note this relies on Tensor t not having changed.
    def redo = _value += t
  }
  case class ZeroTensorDiff(prev: Array[Double]) extends Diff {
    def variable = MutableTensorVar.this
    def undo = value += prev
    def redo = value.zero()
  }}

/** A variable whose value is a cc.factorie.la.Tensor.

    The zero-arg constructor should only be used by subclasses
    (e.g. so that CategoricalVariable can use its domain for value lookup),
    and should never be called by users; otherwise the value will be null. 
    @author Andrew McCallum */
class TensorVariable[T <: Tensor] extends MutableTensorVar[T] {
  def this(initialValue: T) = { this(); set(initialValue)(null) }
  //def domain: TensorDomain = TensorDomain
}

/** Convenience methods for constructing TensorVariables with a Tensor1 of various types.
    @author Andrew McCallum */
object TensorVariable {
  def apply(dim1:Int) = new TensorVariable(new DenseTensor1(dim1))
  def dense(dim1:Int) = new TensorVariable(new DenseTensor1(dim1))
  def sparse(dim1:Int) = new TensorVariable(new SparseTensor1(dim1))
  def sparseBinary(dim1:Int) = new TensorVariable(new SparseBinaryTensor1(dim1))
}
