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
import scala.util.Random
import scala.collection.mutable.ArrayBuffer

trait DiscreteVectorVar extends VectorVar with VarAndValueType[DiscreteVectorVar,DiscreteVectorValue] {
  def domain: DiscreteVectorDomain
  def contains(dimension:Int): Boolean = vector.apply(dimension) != 0.0
}

/** A vector with dimensions corresponding to a DiscreteDomain, and with Double weights for each dimension, represented by a sparse vector. */
abstract class DiscreteVectorVariable extends VectorVariable with DiscreteVectorVar {
  thisVariable =>
  _set(new SparseVector(domain.dimensionSize) with DiscreteVectorValue { def domain = thisVariable.domain })
}

/** A sparse binary vector with length determined by a DiscreteDomain */
trait SparseBinaryDiscreteVectorVar extends DiscreteVectorVar with VarAndValueType[SparseBinaryDiscreteVectorVar,SparseBinaryVector with DiscreteVectorValue] {
  //type ValueType <: cc.factorie.la.SparseBinaryVector with DiscreteVectorValue
  def length = domain.dimensionSize //allocSize // TODO Remove this?
  //def apply(i:Int) = vector.apply(i)
  def activeDomain = vector.activeDomain
  def zero(): Unit = value.zero()
  def +=(i:Int): Unit = { if (frozen) throw new Error("Cannot append to frozen SparseBinaryDiscreteVectorVar."); value.include(i) }
  //def ++=(is:Iterable[Int]): Unit = is.foreach(i => vector.+=(i)) // Conflicts with ++=(Iterable[T])
  var frozen = false
  def freeze() = frozen = true
  override def isConstant = frozen
}

abstract class SparseBinaryDiscreteVectorVariable extends VectorVariable with SparseBinaryDiscreteVectorVar {
  thisVariable =>
  _set(new cc.factorie.la.SparseBinaryVector(-1) with DiscreteVectorValue {
    def domain = thisVariable.domain
    override def length = domain.dimensionSize
  })
}



/** A single discrete variable */
trait DiscreteVar extends DiscreteVectorVar with VarAndValueType[DiscreteVar,DiscreteValue] {
  def domain: DiscreteDomain
  /*@inline final*/ def intValue = value.intValue
  //def activeDomain = List(intValue) // TODO try to make this implementation possible: = value
}

trait MutableDiscreteVar extends DiscreteVar {
  def set(newValue:ValueType)(implicit d:DiffList): Unit
  def set(newInt:Int)(implicit d:DiffList): Unit = set(domain.getValue(newInt))(d)
  def setRandomly(random:Random = cc.factorie.random, d:DiffList = null): Unit = set(random.nextInt(domain.size))(d)
}

// TODO Note that DiscreteVariable is not a subclass of VectorVariable, due to initialization awkwardness.
// Consider fixing this.
/** A Variable holding a single DiscreteValue. */
abstract class DiscreteVariable extends VectorVariable with MutableDiscreteVar with MutableVar with IterableSettings with QDistribution {
  // The base constructor must take no arguments because CategoricalVariable needs to create with a temporary value and do the lookup later.
  def this(initialValue:DiscreteValue) = { this(); require(initialValue.domain == domain); _set(initialValue) }
  def this(initialInt:Int) = { this(); _set(domain.getValue(initialInt)) }
  // Method _set() is defined in VectorVariable
  // Without DiffList
  @deprecated protected def initialValue_=(newValue:ValueType) = _set(newValue)
  // With DiffList
  def set(newValue:ValueType)(implicit d:DiffList): Unit = if (newValue ne value) {
    assert((newValue eq null) || newValue.domain == domain)
    if (d ne null) d += new DiscreteVariableDiff(value, newValue)
    _set(newValue)
  }
  // TODO provide default value for DiffList = null
  def settings = new SettingIterator {
    // TODO Base this on a domain.iterator instead, for efficiency
    var i = -1
    val max = domain.size - 1
    def hasNext = i < max
    def next(difflist:DiffList) = { i += 1; val d = newDiffList; set(i)(d); d }
    def reset = i = -1
    override def variable: DiscreteVariable.this.type = DiscreteVariable.this
  }
  case class DiscreteVariableDiff(oldValue: ValueType, newValue: ValueType) extends Diff {
    @inline final def variable: DiscreteVariable = DiscreteVariable.this
    @inline final def redo = _set(newValue)
    @inline final def undo = _set(oldValue)
    override def toString = variable match { 
      case cv:CategoricalVar[_] if (oldValue.intValue >= 0) => "DiscreteVariableDiff("+cv.domain.getCategory(oldValue.intValue)+"="+oldValue.intValue+","+cv.domain.getCategory(newValue.intValue)+"="+newValue.intValue+")"
      case _ => "DiscreteVariableDiff("+oldValue.intValue+","+newValue.intValue+")"
    }
  }
  // TODO But then this choice cannot be changed by subclasses :-(  Consider some implicit configuration instead.
  // So define a QType[+QT] trait!
  type QType = cc.factorie.generative.MutableProportions  // TODO Change this = to <:  Why wasn't this done before?
  def newQ = new cc.factorie.generative.DenseProportions(domain.size)
}


/** A compact array of DiscreteValues. */
/*
abstract class DiscreteArrayVariable(initialIntValues:Seq[Int]) extends VarAndValueType[DiscreteArrayVariable,Seq[DiscreteValue]] with ArrayVar[DiscreteValue] {
  //def domain: DiscreteArrayDomain
  private val _values: ArrayBuffer[Value] = 
    new ArrayBuffer[Value](if (initialIntValues.length > 0) initialIntValues.length else 8) ++= initialIntValues.map(i => domain.getValue(i))
  @inline final def arraySize = _values.length
  def appendValue(v:ValueType): Unit = _values += v
  def appendInt(i:Int): Unit = _values += domain.getValue(i)
  def value: Seq[Value] = _values
  def valueAt(index:Int): Value = _values(index)
  def setAt(newValue:ValueType, index:Int)(implicit d:DiffList): Unit = {
    require (d eq null) // DiffList not yet implemented for this change
    _values(index) = newValue
  }
}
*/

/** A collection of DiscreteVariables that can iterate over the cross-product of all of their values.  May be useful in the future for block-Gibbs-sampling?
    @author Andrew McCallum */
@deprecated("This will likely be removed in a future version.")
class DiscreteVariableBlock(vars:DiscreteVariable*) extends Variable with Seq[DiscreteVariable] with IterableSettings with VarAndValueGenericDomain[DiscreteVariableBlock,List[Int]] {
  def value = _vars.map(_.intValue)
  private val _vars = vars.toList
  def length = _vars.length
  def apply(index:Int) = _vars.apply(index)
  def iterator = _vars.iterator
  override def foreach[U](f:(DiscreteVariable)=>U): Unit = _vars.foreach(f)
  def settings: SettingIterator = new SettingIterator {
    var i = -1
    val n = _vars.length
    val s = _vars.map(_.domain.size).toArray
    val max = s.foldLeft(1)(_*_)
    def hasNext = i < max
    def next(difflist:DiffList) = throw new Error // TODO Implement this properly { i += 1; val d = newDiffList; _vars(i%n).set(i/n)(d); d }
    def reset = i = -1
  }
}
