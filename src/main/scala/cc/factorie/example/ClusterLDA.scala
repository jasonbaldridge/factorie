package cc.factorie.example

import scala.collection.mutable.{ArrayBuffer,HashMap,HashSet,ListBuffer}
import scala.collection.jcl.WeakHashMap
import scala.util.Sorting
import scala.reflect.Manifest
import scala.util.matching.Regex
import java.io.File
import cc.factorie.util.Stopwords
import cc.factorie.util.Implicits._

/** Implements "Cluster LDA" from Hanna Wallach's thesis.
    Runs, but would benefit from a DirichletMultinomial implementation. */

object ClusterLDADemo {

  // Declare different types of variables
	object Beta extends SymmetricDirichlet[Word](0.01)
  class Topic extends Multinomial[Word] with MixtureComponent[Topic]
  class Z extends MixtureChoice[Topic,Z] with NoFactorCoordination; Domain.alias[Z,Topic]
  class Alpha extends Dirichlet[Z](1.0) with DirichletMomentMatchingEstimator[Z] with MixtureComponent[Alpha]
  class Y extends MixtureChoice[Alpha,Y]; Domain.alias[Y,Alpha]
  object Gamma extends UniformMultinomial[Y]
	class Theta extends Multinomial[Z]
	class Word(s:String) extends EnumVariable(s) with MultinomialOutcome[Word]
 	class Document(val file:String) extends ArrayBuffer[Word] { var theta:Theta = _; var y:Y = _ }

  def main(args: Array[String]) : Unit = {
  	// Read observed data and create Documents
		var documents = new ListBuffer[Document];
		val lexer = new Regex("[a-zA-Z]+")
		for (directory <- if (args.length > 0) args else datadirs1) {
			for (file <- new File(directory).listFiles; if (file.isFile)) {
				val d = new Document(file.toString)
				d ++= lexer.findAllIn(file.contentsAsString).toList.map(_ toLowerCase).filter(!Stopwords.contains(_)).map(new Word(_))
				documents += d
			}
		}
		println("Read "+documents.size+" documents with "+documents.foldLeft(0)(_+_.size)+" tokens and "+Domain[Word].size+" types.")
		documents.trimEnd(documents.size-200)
		println("Processing "+documents.size+" documents with "+documents.foldLeft(0)(_+_.size)+" tokens and "+Domain[Word].size+" types.")  

  
  // Create random variables
    val numTopics = 8
    val numClusters = 4
    val topics : Array[Topic] = for (i <- Array.range(0, numTopics)) yield new Topic ~ Beta
    val alphas : Array[Alpha] = for (i <- Array.range(0, numClusters)) yield new Alpha
		val zs = new ArrayBuffer[Z]
  	for (document <- documents) {
  	  document.y = new Y ~ Gamma // select a cluster
  		document.theta = new Theta ~ document.y // generate a topic distribution from that cluster's Dirichlet
  		for (word <- document) {
  			val z = new Z ~ document.theta // select a topic for this word
  			word ~ z // declare that the word was generated by that topic
  			zs +=z // just to gather the variables we need to sample later 
  		}
  	}
    
		// Fit model
		val sampler = new GibbsSampler1
		val startTime = System.currentTimeMillis
    for (i <- 1 to 50) {
      sampler.sample(zs, 1)
    	zs.foreach(sampler.sample(_))
    	print("."); Console.flush
    	if (i % 5 == 0) {
    		println ("Iteration "+i)
    		topics.foreach(t => println("Topic "+t.index+"  "+t.top(20).map(_._1)))
    		println
      }
    	if (i % 10 == 0) {
    	  // Fit the clusters' Dirichlets
    	  repeat(2) { sampler.sample(documents.map(_.y), 1); alphas.foreach(_.estimate) }
    	  alphas.foreach(a => println("Alpha %2d %5d %s".format(a.index, a.generatedSamples.size, a.alphas.toList.toString)))
    	}
    }	
    topics.foreach(t => {println("Topic "+t.index); t.top(20).foreach(x => println("%-16s %f".format(x._1,x._3)))})
    alphas.foreach(a => {
      println("Alpha %2d %5d %s".format(a.index, a.generatedSamples.size, a.alphas.toList.toString))
      a.alphas.toList.zipWithIndex.sortReverse(_._1).foreach(ai => println("Topic %2d %s".format(ai._2, topics(ai._2).topWords(5).toString)))
    })
		println("Finished in "+((System.currentTimeMillis-startTime)/1000.0)+" seconds")
    
		0
	}
  
  def repeat(n:Int)(c: =>Unit) : Unit = for (i <- 0 until n) c
  
  val datadirs1 = Array(
"/Users/mccallum/research/data/text/nipstxt/nips10",
"/Users/mccallum/research/data/text/nipstxt/nips11",
"/Users/mccallum/research/data/text/nipstxt/nips12"
  )
  val datadirs2 = Array(
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/acq",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/alum",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/barley",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/bop",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/carcass",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/castor-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/cocoa",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/coconut",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/coconut-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/coffee",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/copper",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/copra-cake",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/corn",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/cotton",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/cotton-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/cpi",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/cpu",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/crude",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/dfl",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/dlr",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/dmk",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/earn",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/fuel",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/gas",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/gnp",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/gold",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/grain",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/groundnut",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/groundnut-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/heat",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/hog",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/housing",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/income",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/instal-debt",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/interest",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/ipi",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/iron-steel",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/jet",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/jobs",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/l-cattle",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/lead",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/lei",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/lin-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/livestock",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/lumber",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/meal-feed",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/money-fx",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/money-supply",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/naphtha",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/nat-gas",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/nickel",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/nkr",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/nzdlr",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/oat",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/oilseed",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/orange",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/palladium",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/palm-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/palmkernel",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/pet-chem",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/platinum",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/potato",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/propane",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/rand",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/rape-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/rapeseed",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/reserves",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/retail",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/rice",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/rubber",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/rye",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/ship",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/silver",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/sorghum",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/soy-meal",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/soy-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/soybean",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/strategic-metal",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/sugar",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/sun-meal",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/sun-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/sunseed",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/tea",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/tin",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/trade",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/veg-oil",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/wheat",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/wpi",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/yen",
"/Users/mccallum/research/data/text/reuters/reuters-parsed/modapte/zinc"
  )
}
