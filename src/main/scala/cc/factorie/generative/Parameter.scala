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

package cc.factorie.generative
import cc.factorie._
import scala.collection.mutable.{HashSet,ArrayBuffer}

trait Parameter extends GeneratedVar {
  private val _childFactors = new scala.collection.mutable.ArrayBuffer[GenerativeFamily#Factor]
  override def factors: Seq[GenerativeFamily#Factor] = parentFactor +: _childFactors
  def addChildFactor(f:GenerativeFamily#Factor): Unit = _childFactors += f
  def removeChildFactor(f:GenerativeFamily#Factor): Unit = _childFactors -= f
  def addChild(v:GeneratedVar, d:DiffList = null): Unit = _childFactors += v.parentFactor
  def removeChild(v:GeneratedVar, d:DiffList = null): Unit = _childFactors -= v.parentFactor
  def childFactors: Seq[GenerativeFamily#Factor] = _childFactors
  def children: Seq[GeneratedVar] = _childFactors.map(_.variables.head).asInstanceOf[Seq[GeneratedVar]]
  def childrenOfClass[A](implicit m:Manifest[A]) = children.filter(_.getClass == m.erasure).asInstanceOf[Iterable[A]]
  /** A collection of variables whose value depends on the value of this variable, 
      either directly or via a sequence of deterministic variables.  If this variable's
      value changes, all of these extended children variables' .pr will change. */
  def extendedChildren: Iterable[GeneratedVar] = {
    val result = new ArrayBuffer[GeneratedVar]
    for (child <- children) {
      child match { 
        case dp:DeterministicParameter => { result += dp; result ++= dp.extendedChildren }
        case gv:GeneratedVar => result += gv
      }
    }
    result
  }
  def deprecated_weightedGeneratedChildren(map:scala.collection.Map[Variable,Variable]): Iterable[(GeneratedVar,Double)] = {
    val result = new ArrayBuffer[(GeneratedVar,Double)]
    for (child <- children) map.getOrElse(child,child) match {
      /*case mcs:MixtureComponents[_] => {
        val mci = mcs.components.indexOf(this); assert(mci >= 0)
        for (c <- mcs.children) map.getOrElse(c,c) match {
          case mv:MixtureGeneratedVar => map.getOrElse(mv.choice, mv.choice) match {
            case mc:MixtureChoice => if (mc.intValue == mci) result += ((c, 1.0))
            case pr:Proportions => { assert(pr.length == mcs.components.length); if (pr(mci) > 0.0) result += ((c, pr(mci))) }
          }
        }
      }*/
      case gv:GeneratedVar => result += ((child, 1.0))
    }
    result
  }
}


trait DeterministicParameter extends GeneratedVar with Parameter {
  override final def isDeterministic = true
}



// TODO I'm not yet convinced that this "Estimation" interface is a good idea.
// It is a "short-cut" that bypasses the model, like GenerativeVariable.sampleFromParents.
// But it leads to passing around odd "mapping" arguments, etc.
// Perhaps parameter estimation should really just be handled by proper Inferencer's
// with all the proper checks for the model's factors. 
// On the other hand, when considering where to put "variable-class-specific functionality"
// a balance needs be struck between "in the variables" and "in the general inference";
// perhaps this is not a bad balance?
// -akm
/*
trait Estimation[This<:Estimation[This] with Parameter] {
  this: This =>
  def defaultEstimator: Estimator[This]
  def estimate(estimator:Estimator[This] = defaultEstimator, mapping:scala.collection.Map[Variable,Variable] = Map[Variable,Variable]()): Unit = defaultEstimator.estimate(this, mapping)
}

trait Estimator[P<:Parameter] {
  // Estimate a parameter conditioned only on its parents and children, 
  //   using the optional 'map' to make substitutions. 
  //    This does not do full general inference of many dependent variables/parameters;
  //    in that respect it is somewhat analagous to GeneratedVariable.sampleFromParents().
  // TODO Consider renaming estimateFromParentsAndChildren ?
  def estimate(parameter:P, mapping:scala.collection.Map[Variable,Variable] = Map[Variable,Variable]()): Unit
}

class IIDEstimationInferencer[P<:Parameter with Estimation[P]] extends VariableInferencer[P] {
  type LatticeType = Lattice[P]
  def infer(variables:Iterable[P], varying:Iterable[P]): LatticeType = infer(variables, varying, Map[Variable,Variable]())
  def infer(variables:Iterable[P], varying:Iterable[P], mapping:scala.collection.Map[Variable,Variable]): LatticeType = {
    variables.foreach(v => v.estimate(v.defaultEstimator, mapping))
    new Lattice[P] {}
  }
}
*/



trait AbstractParameterRef extends Variable {
  //def abstractValue: AnyRef //Parameter
  def child: GeneratedVar
}
class ParameterRef[P<:Parameter,C<:GeneratedVar](p:P, override val child:C) extends RefVariable(p) with AbstractParameterRef {
  p.addChild(child)
  //println("ParameterRef.init parent="+p.getClass.getName+"@"+p.hashCode+" child="+child)
  // This 'set' method is no longer called in initialization of RefVariable, hence line above
  override def set(newValue:P)(implicit d:DiffList): Unit = if (newValue ne value) { 
    // Above, if this is != instead of ne, then entire Proportion contents would be examined (if not for IndexedSeqEqualsEq)!  Slow!!!
    if (value ne null) value.removeChild(child)
    super.set(newValue)
    if (value ne null) value.addChild(child)
  }
}
/*
@deprecated("This will go away in the future.")
class GatedParameterRef[P<:Parameter,C<:MixtureGeneratedVar](val parameters:Seq[P], val gate:Gate, child:C) extends ParameterRef[P,C](parameters.apply(gate.intValue), child) with GatedRefVariable[P] {
  //println("GatedParameterRef child="+child)
  gate += this // xxx
  assert(parameters.length == gate.domain.size)
  def valueForIndex(index:Int) = parameters(index)
  def domainSize = parameters.length
}
*/


trait DeterministicFunction extends Parameter
trait RealFunction extends DeterministicFunction with RealVarParameter
abstract class RealOpConstant(val real:RealVarParameter) extends RealFunction with GeneratedVar {
  real.addChild(this) // But now might not garbage collect this when we want to
  override def parents = IndexedSeq(real)
  override def pr = 1.0 // Deterministic value given parent
  //def prFrom(parents:Seq[Parameter]) = 1.0
}
class RealPlusConstant(override val real:RealVarParameter, val constant:Double) extends RealOpConstant(real) {
  val generativeTemplate = throw new Error
  def generativeFactor = throw new Error
  def doubleValue = real.doubleValue + constant
}
class RealTimesConstant(override val real:RealVarParameter, val constant:Double) extends RealOpConstant(real) {
  val generativeTemplate = throw new Error
  def generativeFactor = throw new Error
  def doubleValue = real.doubleValue * constant
}

