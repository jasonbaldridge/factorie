package cc.factorie.er

/** Defines a reversable many-to-many relation. */
trait Getter0[A<:Variable,B<:Variable] {
	def forward : A=>Iterable[B];
	def reverse : B=>Iterable[A];
	def ->[C<:Variable](g:Getter0[B,C]) : Getter0[A,C]
}
/** Defines a reversable one-to-one relation. */
trait Getter1[A<:Variable,B<:Variable] extends Getter0[A,B] {
	def forward1 : A=>B
	def reverse1 : B=>A
	def forward = (a:A) => { val f1 = forward1(a); if (f1 == null) Nil else List(f1) }
	def reverse = (b:B) => { val r1 = reverse1(b); if (r1 == null) Nil else List(r1) }
	def apply(a:A) : B = forward1(a)
	def unapply(b:B) : A = reverse1(b)
}
  /** A reversable many-to-many relation with re-defined apply and unapply methods. */
  trait GetterN[A<:Variable,B<:Variable] extends Getter0[A,B] {
    def apply(a:A) : Iterable[B] = forward(a)
    def unapply(b:B) : Iterable[A] = reverse(b)
    def ->[C<:Variable](g:Getter0[B,C]) : Getter[A,C] = new Getter[A,C](b=>forward(b).flatMap(g.forward), d=>g.reverse(d).flatMap(reverse))
  }
  /** Describes how to go from a owner to its attribute (forward), and from the attribute to its owner (reverse) */
  class AttributeGetter[A<:Variable,B<:Variable with Attribute { type AttributeOwnerType = A }](override val forward1:A=>B) extends Getter1[A,B] {
    def reverse1 : B=>A = (b:B) => b.owner
    //def ->[C<:Variable](g:Getter0[B,C]) : Getter[A,C] = new Getter[A,C](b=>forward(b).flatMap(g.forward), d=>g.reverse(d).flatMap(reverse))
    def ->[C<:Variable](g:Getter0[B,C]) = new Getter[A,C](a=>g.forward(this.forward1(a)), b=>g.reverse(b).map(this.reverse1))
  }

  /** The beginning of a chain of Getters.  Argument position indicates the order of Args in a multi-argument expression. */
  case class Arg[A<:Variable](position:Int) extends Getter0[A,A] {
    def forward = (a:A) => List(a)
    def reverse = (a:A) => List(a)
    def ->[B<:Variable](g:Getter0[A,B]) : Getter0[A,B] = g
  }
  class Getter[A<:Variable,B<:Variable](override val forward:A=>Iterable[B], override val reverse:B=>Iterable[A]) extends GetterN[A,B] 
