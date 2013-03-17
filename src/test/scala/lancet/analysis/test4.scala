package lancet
package analysis

class TestAnalysis4 extends FileDiffSuite {

  val prefix = "test-out/test-analysis-4"

/* 
  make loop ranges explicit, reason about
  an infinite number of memory addresses
  (allocation site indexed by loop iteration)

  TODO -- UNFINISHED
*/

/*



*/

  object Test1 {

    // *** util

    def captureOutputResult[A](func: => A): (String,A) = {
      import java.io._
      val bstream = new ByteArrayOutputStream
      val r = withOutput(new PrintStream(bstream))(func) //func
      (bstream.toString, r)
    }
    def withOutput[A](out: java.io.PrintStream)(func: => A): A = {
      //val oldStdOut = System.out
      //val oldStdErr = System.err
      try {
        //System.setOut(out)
        //System.setErr(out)
        scala.Console.withOut(out)(scala.Console.withErr(out)(func))
      } finally {
        out.flush()
        out.close()
        //System.setOut(oldStdOut)
        //System.setErr(oldStdErr)
      }
    }


    // *** intermediate language / IR interfaces

    abstract class GVal {
      override def toString: String = this match {
        case GRef(s)   => s
        case GConst(x: String) => "\""+x+"\""
        case GConst(x) => s"$x"
      }
    }

    case class GRef(s: String) extends GVal
    case class GConst(x: Any) extends GVal

    abstract class Def {
      override def toString: String = mirrorDef(this, DString)
    }

    case class DMap(m: Map[GVal,GVal]) extends Def
    case class DUpdate(x: GVal, f: GVal, y: GVal) extends Def
    case class DSelect(x: GVal, f: GVal) extends Def
    case class DPlus(x: GVal, y: GVal) extends Def
    case class DTimes(x: GVal, y: GVal) extends Def
    case class DLess(x: GVal, y: GVal) extends Def
    case class DPair(x: GVal, y: GVal) extends Def
    case class DIf(c: GVal, x: GVal, y: GVal) extends Def
    case class DFixIndex(c: GVal) extends Def
    case class DCall(f: GVal, x: GVal) extends Def
    case class DFun(f: String, x: String, y: GVal) extends Def
    case class DOther(s: String) extends Def

    def mirrorDef(d: Def, dst: DIntf { type From >: GVal }): dst.To = d match {
      case DMap(m)                            => dst.map(m.asInstanceOf[Map[dst.From, dst.From]])
      case DUpdate(x: GVal, f: GVal, y: GVal) => dst.update(x,f,y)
      case DSelect(x: GVal, f: GVal)          => dst.select(x,f)
      case DPlus(x: GVal, y: GVal)            => dst.plus(x,y)
      case DTimes(x: GVal, y: GVal)           => dst.times(x,y)
      case DLess(x: GVal, y: GVal)            => dst.less(x,y)
      case DPair(x: GVal, y: GVal)            => dst.pair(x,y)
      case DIf(c: GVal, x: GVal, y: GVal)     => dst.iff(c,x,y)
      case DFixIndex(c: GVal)                 => dst.fixindex(c)
      case DCall(f: GVal, x: GVal)            => dst.call(f,x)
      case DFun(f: String, x: String, y: GVal)=> dst.fun(f,x,y)
      case DOther(s: String)                  => dst.other(s)
    }

    trait DIntf {
      type From
      type To
      def map(m: Map[From,From]): To
      def update(x: From, f: From, y: From): To
      def select(x: From, f: From): To
      def plus(x: From, y: From): To
      def times(x: From, y: From): To
      def less(x: From, y: From): To
      def pair(x: From, y: From): To
      def iff(c: From, x: From, y: From): To
      def fixindex(c: From): To
      def call(f: From, x: From): To
      def fun(f: String, x: String, y: From): To
      def other(s: String): To
    }

    object DString extends DIntf {
      type From = Any
      type To = String
      def map(m: Map[From,From])            = s"$m"
      def update(x: From, f: From, y: From) = s"$x + ($f -> $y)"
      def select(x: From, f: From)          = s"$x($f)"
      def plus(x: From, y: From)            = s"$x + $y"
      def times(x: From, y: From)           = s"$x * $y"
      def less(x: From, y: From)            = s"$x < $y"
      def pair(x: From, y: From)            = s"($x,$y)"
      def iff(c: From, x: From, y: From)    = s"if ($c) $x else $y"
      def fixindex(c: From)                 = s"fixindex($c)"
      def call(f: From, x: From)            = s"$f($x)"
      def fun(f: String, x: String, y: From)= s"{ $x => $y }"
      def other(s: String)                  = s
    }

    object DDef extends DIntf {
      type From = GVal
      type To = Def
      def map(m: Map[From,From])            = DMap(m)
      def update(x: From, f: From, y: From) = DUpdate(x,f,y)
      def select(x: From, f: From)          = DSelect(x,f)
      def plus(x: From, y: From)            = DPlus(x,y)
      def times(x: From, y: From)           = DTimes(x,y)
      def less(x: From, y: From)            = DLess(x,y)
      def pair(x: From, y: From)            = DPair(x,y)
      def iff(c: From, x: From, y: From)    = DIf(c,x,y)
      def fixindex(c: From)                 = DFixIndex(c)
      def call(f: From, x: From)            = DCall(f,x)
      def fun(f: String, x: String, y: From)= DFun(f,x,y)
      def other(s: String)                  = DOther(s)
    }

    trait DXForm extends DIntf {
      type From
      type To
      val next: DIntf
      def pre(x: From): next.From
      def post(x: next.To): To
      def map(m: Map[From,From])            = post(next.map(m.map(kv=>pre(kv._1)->pre(kv._2))))
      def update(x: From, f: From, y: From) = post(next.update(pre(x),pre(f),pre(y)))
      def select(x: From, f: From)          = post(next.select(pre(x),pre(f)))
      def plus(x: From, y: From)            = post(next.plus(pre(x),pre(y)))
      def times(x: From, y: From)           = post(next.times(pre(x),pre(y)))
      def less(x: From, y: From)            = post(next.less(pre(x),pre(y)))
      def pair(x: From, y: From)            = post(next.pair(pre(x),pre(y)))
      def iff(c: From, x: From, y: From)    = post(next.iff(pre(c),pre(x),pre(y)))
      def fixindex(c: From)                 = post(next.fixindex(pre(c)))
      def call(f: From, x: From)            = post(next.call(pre(f),pre(x)))
      def fun(f: String, x: String, y: From)= post(next.fun(f,x,pre(y)))
      def other(s: String)                  = post(next.other(s))
    }

    object IRS extends DXForm {
      type From = String
      type To = String
      val next = DString
      def const(x: Any) = s"$x"
      def pre(x: String) = x
      def post(x: String): String = reflect(x)
      override def fun(f: String, x: String, y: From) = reflect(f,next.fun(f,x,pre(y)))
    }

    object IRD extends DXForm {
      type From = GVal
      type To = GVal
      val next = DDef
      def const(x: Any) = GConst(x)
      def pre(x: GVal) = x
      def post(x: Def): GVal = dreflect(x)
      //override def fun(f: String, x: String, y: From) = dreflect(f,next.fun(f,x,pre(y)))

      object Def {
        def unapply(x:GVal): Option[Def] = findDefinition(x.toString)
      }

      // dependencies / schedule
      def syms(d: Def): List[String] = {
        var sl: List[String] = Nil
        object collector extends DXForm {
          type From = GVal
          type To = String // ignore
          val next = DString
          def pre(x: GVal) = x match { case GRef(s) => sl ::= s; s case _ => "" }
          def post(x: String) = x
          //override def fun(f: String,x: String,y: GVal) = ""
        }
        mirrorDef(d,collector)
        sl
      }
      def boundSyms(d: Def): List[String] = d match { case DFun(f,x,y) => List(f,x) case _ => Nil }
      def deps(st: List[String]): List[(String,Def)] =
        globalDefs.filter(p=>st contains p._1)
      def schedule(x: GVal) = {
        val start = x match { case GRef(s) => List(s) case _ => Nil }
        val xx = scala.virtualization.lms.util.GraphUtil.stronglyConnectedComponents[(String,Def)](deps(start), t => deps(syms(t._2)))
        xx.flatten.reverse
      }

      def printStm(p: (String,Def)) = println(s"val ${p._1} = ${p._2}")

      def dependsOn(a: GVal, b: GVal) = schedule(a).exists(p => GRef(p._1) == b || syms(p._2).contains(b.toString))

      // evaluate with substitution, i.e. compute trans closure of subst
      def substTrans(env0: Map[GVal,GVal]): Map[GVal,GVal] = {
        var env = env0
        object XXO extends DXForm {
          type From = GVal
          type To = GVal
          val next = IRD
          def pre(x: GVal) = {/*println(s"pre $x / $env");*/ env.getOrElse(x,x) }
          def post(x: GVal) = x
          override def fun(f: String, x: String, y: From) = {
            //println(s"not changing fundef $f $x $y -> ${pre(y)}")
            GRef(f) // don't transform fundef
          }
        }
        for ((e,d) <- globalDefs) {
          val e2 = mirrorDef(d,XXO)
          //println(s"$e -> $e2 = $d")
          if (e2 != GRef(e))
            env = env + (GRef(e) -> e2)
        }
        // need to iterate because of sched order
        if (env == env0) env else substTrans(env)
      }

      // perform iterative optimizations
      def iterateAll(res: GVal): GVal = {
        println("*** begin iterate: "+res)

        val sched = schedule(res)

        val funs = sched collect { case p@(a,DFun(f,x,z)) => p }
        val calls = sched collect { case p@(a,DCall(f,y)) => p }

        println("funs:")
        funs foreach printStm

        println("calls:")
        calls foreach printStm

        // base case: eval function bodies for i = 0

        val subst = funs map {
          case (a,DFun(f,x,z)) =>
            GRef(x) -> GConst(0)
        }

        println("subst: "+subst.toMap)
        val zeroSubst = substTrans(subst.toMap)

        val zeros = funs map {
          case (a,DFun(f,x,z)) =>
            a -> zeroSubst.getOrElse(z,z) // alt: this.subst(z,GRef(x),GConst(0))
        }

        println("zeros: "+zeros.toMap)

        // induction case: if zero iteration evaluates to a map, split function
        def mkey(f: String, x: GVal) = x match {
          case GConst(s) => f+"_"+s
          case GRef(s) => f+"_"+s
        }

        // replace all calls. TODO: handle isolated fixindex nodes?
        val xform = calls flatMap {
          case (a,DCall(f,z)) =>
            zeros.toMap.apply(f.toString) match {
              case Def(DMap(m)) =>
                def func(k: GVal) = GRef(mkey(f.toString,k))
                def arg(k: GVal) = z match {
                  case Def(DFixIndex(`f`)) => fixindex(func(k))
                  case _ => z
                }
                List(GRef(a) -> map(m map (kv => kv._1 -> call(func(kv._1), arg(kv._1)))))
              case _ => Nil
            }
        }

        println("xform: "+xform.toMap)
        val xformSubst = substTrans(xform.toMap)

        // generate new fundefs
        funs foreach {
          case (a,DFun(f,x,z)) => 
            zeros.toMap.apply(f.toString) match {
              case Def(DMap(m)) =>
                def func(k: GVal) = GRef(mkey(f.toString,k))
                def body(k: GVal) = select(xformSubst(z),k)
                m foreach (kv => kv._1 -> fun(func(kv._1).toString,x,body(kv._1)))
              case _ =>
                if (xformSubst.contains(z)) {
                  // HACK -- unsafe???
                  globalDefs = globalDefs.filterNot(_._1 == f)
                  fun(f,x,xformSubst(z))
                  println(s"### fun has been xformed: $a = _=> $z")
                }
            }
        }

        val res1 = xformSubst.getOrElse(res,res)
        println("*** done iterate: "+res1)
        if (res1 != res) iterateAll(res1) else res1
      }



      def subst(x: GVal, a: GVal, b: GVal): GVal = x match {
        case `a`                 => b
        case GConst(_)           => x
        case Def(DUpdate(x,f,y)) => update(subst(x,a,b),subst(f,a,b),subst(y,a,b))
        case Def(DSelect(x,f))   => select(subst(x,a,b),subst(f,a,b))
        case Def(DMap(m))        => map(m.map(kv => subst(kv._1,a,b) -> subst(kv._2,a,b)))
        case Def(DMap(m))        => map(m.map(kv => subst(kv._1,a,b) -> subst(kv._2,a,b)))
        case Def(DIf(c,x,y))     => iff(subst(c,a,b),subst(x,a,b),subst(y,a,b))
        case Def(DPlus(x,y))     => plus(subst(x,a,b),subst(y,a,b))
        case Def(DTimes(x,y))    => times(subst(x,a,b),subst(y,a,b))
        case Def(DLess(x,y))     => less(subst(x,a,b),subst(y,a,b))
        case Def(DCall(f,y))     => call(subst(f,a,b),subst(y,a,b))
        case Def(DFun(f,x1,y))   => x//subst(y,a,b); x // binding??
        case Def(_)              => println("no subst: "+x); x
        case _                   => x // TOOD
      }


      override def update(x: From, f: From, y: From) = x match {
        case GConst(m:Map[_,_]) if m.isEmpty => map(Map(f -> y))
        case Def(DMap(m)) => map(m + (f -> y)) // only if const!
        case _ => super.update(x,f,y)
      }
      override def select(x: From, f: From)          = x match {
        //case GConst(m:Map[From,From]) => GConst(m.getOrElse(f,GConst("nil"))) // f must be const!
        case Def(DMap(m)) => m.getOrElse(f, super.select(x,f))
        case Def(DUpdate(x2,f2,y2)) => if (f2 == f) y2 else select(x2,f)
        case Def(DIf(c,x,y)) => iff(c,select(x,f),select(y,f))
        case _ => super.select(x,f)
      }
      override def plus(x: From, y: From)            = (x,y) match {
        case (GConst(x:Int),GConst(y:Int)) => GConst(x+y)
        case (GConst(0),_) => y
        case (_,GConst(0)) => x
        case (Def(DPlus(a,b)),_) => plus(a,plus(b,y))
        case (Def(DIf(c,x,z)),_) => iff(c,plus(x,y),plus(z,y))
        case _ => super.plus(x,y)
      }
      override def times(x: From, y: From)            = (x,y) match {
        case (GConst(x:Int),GConst(y:Int)) => GConst(x*y)
        case (GConst(0),_) => GConst(0)
        case (_,GConst(0)) => GConst(0)
        case (GConst(1),_) => y
        case (_,GConst(1)) => x
        case (Def(DIf(c,x,z)),_) => iff(c,times(x,y),times(z,y))
        case _ => super.times(x,y)
      }
      override def less(x: From, y: From)            = (x,y) match {
        case (GConst(x:Int),GConst(y:Int)) => GConst(if (x < y) 1 else 0)
        case (Def(DPlus(a,GConst(b:Int))),c) =>  less(a,plus(c,const(-b)))// random rewrite ...
        case (Def(DIf(c,x,z)),_) => iff(c,less(x,y),less(z,y))
        // case (GConst(0),Def(DPlus())) => y
        case _ => super.less(x,y)
      }
      override def pair(x: From, y: From)            = super.pair(x,y)
      override def iff(c: From, x: From, y: From)    = c match {
        case GConst(0) => y
        case GConst(_) => x
        case Def(DIf(c1,x1,y1)) => iff(c1,iff(x1,x,y),iff(y1,x,y))
        case _ if x == y => x
        case _ => 
          (x,y) match {
            case (Def(DMap(m1)), Def(DMap(m2))) => 
              // push inside maps
              map((m1.keys++m2.keys) map { k => k -> iff(c,m1.getOrElse(k,const("nil")),m2.getOrElse(k,const("nil")))} toMap)
            case _ =>
              // generate node, but remove nested tests on same condition
              super.iff(c,subst(x,c,GConst(1)),subst(y,c,GConst(0)))
          }
      }
      override def fixindex(c: From)                 = super.fixindex(c)
      override def call(f: From, x: From)            = f match {
        case Def(DFun(f1,x1,y1)) if !dependsOn(y1,f) && !dependsOn(y1,x) =>
          println(s"*** will inline call $f($x1) = $y1 / $x1 -> $x")
          subst(y1,GRef(x1),x)
        case _ =>
          super.call(f,x)
      }

      override def fun(f: String, x: String, y: From) = y match {
        // try to remove loop carried deps! TODO: make more principled ...
        // if (0 < x) { if (loopc) f(x-1) + d else f(x-1) } else zeroRes --> 
        // if (0 < x) { if (loopc) d else f(x-1) } else zeroRes
        case Def(DIf(zc @ Def(DLess(GConst(0),GRef(`x`))),
          Def(DIf(loopc, 
            incRes, 
            prevRes @ Def(DCall(GRef(`f`),prevx @ Def(DPlus(GRef(`x`),GConst(-1))))))),
          zeroRes)) =>

          println(s"fun $f = $zeroRes -> while($loopc) $x -> $incRes")

          incRes match {
            case Def(DPlus(`prevRes`, d)) if !dependsOn(d,GRef(x)) => 
              println(s"invariant stride $d")
              println(s"result = $zeroRes + $x * $d")
              val prev1 = plus(times(prevx,d), zeroRes)
              val cond1 = subst(loopc, prevRes,prev1)
              println(s"loopc1 $cond1")
              val max = cond1 match {
                case Def(DLess(GRef(`x`), y)) => plus(y,const(-1))
                case _ => GRef("max_"+x)
              }
              println(s"subst $prevRes -> $prev1")
              val y1 = subst(y,prevRes,iff(zc,iff(cond1,prev1,max),zeroRes))
              println("sym " +y1)
              fun(f,x,y1)
            case GConst(n) => 
              println(s"const res $n")
              println(s"result = $n")
              dreflect(f,next.fun(f,x,pre(y)))            
            case _ =>
              dreflect(f,next.fun(f,x,pre(y)))            
          }


        // if (loopc) { if (0 < x) incRes else zeroRes } else if (0 < x) f(x-1)
        case Def(DIf(loopc,
          incRes  ,//@ Def(DIf(Def(DLess(GConst(0),`x`)), zeroRes)),
          prevRes @ Def(DIf(Def(zc @ DLess(GConst(0),GRef(`x`))), 
            prevCall @ Def(DCall(GRef(`f`),Def(DPlus(GRef(`x`),GConst(-1))))), 
            zeroRes)))) =>

          println(s"fun $f = $zeroRes -> while($loopc) $x -> $incRes")

          val d = incRes match {
            case Def(DIf(_,Def(DPlus(`prevCall`, d)), _)) => 
              println(s"stride $d")
            case _ =>
          }


          dreflect(f,next.fun(f,x,pre(y)))            
        case _ =>
          dreflect(f,next.fun(f,x,pre(y))) // reuse fun sym (don't call super)
      }

    }


    // reflect/reify

    val varCount0 = 0
    var varCount = varCount0

    val globalDefs0 = Nil 
    var globalDefs: List[(String,Def)] = globalDefs0

    def freshVar = { varCount += 1; "x"+(varCount - 1) }

    def reflect(x: String, s: String): String = { println(s"val $x = $s"); x }

    def reflect(s: String): String = { val x = freshVar; println(s"val $x = $s"); x }
    def reify(x: => String): String = captureOutputResult(x)._1

    def findDefinition(s: String): Option[Def] = globalDefs.reverse.collectFirst { case (`s`,d) => d }

    def dreflect(x0: => String, s: Def): GVal = globalDefs.collect { case (k,`s`) => GRef(k) }.headOption getOrElse { 
      val x = x0; globalDefs = globalDefs :+ (x->s); println(s"val $x = $s"); GRef(x) }
    def dreflect(s: Def): GVal = dreflect(freshVar,s)


    // *** input language Exp

    val IR = IRD

    type Val = IR.From
    type Var = String
    type Addr = String
    type Alloc = String
    type Field = String

    def vref(x: String): Val = IR.const(x)

    abstract class Exp
    case class Const(x: Int) extends Exp
    case class Direct(x: Val) extends Exp
    case class Ref(x: Var) extends Exp
    case class Assign(x: Var, y: Exp) extends Exp
    case class Plus(x: Exp, y: Exp) extends Exp
    case class Less(x: Exp, y: Exp) extends Exp
    case class New(x: Alloc) extends Exp
    case class Get(x: Exp, f: Field) extends Exp
    case class Put(x: Exp, f: Field, y: Exp) extends Exp
    case class If(c: Exp, a: Exp, b: Exp) extends Exp
    case class While(c: Exp, b: Exp) extends Exp
    case class Block(xs: List[Exp]) extends Exp {
      override def toString = "{\n  " + xs.map(_.toString).mkString("\n").replace("\n","\n  ") + "\n}"
    }

    // *** evaluator: Exp -> IR

    val store0 = IR.const(Map())
    val itvec0 = IR.const(1)

    var store: Val = store0
    var itvec: Val = itvec0

    def eval(e: Exp): Val = e match {
      case Const(x)    => IR.const(x)
      case Direct(x)   => IR.const(x)
      case Ref(x)      => IR.select(IR.select(store,IR.const("&"+x)), IR.const("val"))
      case Assign(x,y) => 
        store = IR.update(store, IR.const("&"+x), IR.update(IR.const(Map()), IR.const("val"), eval(y)))
        IR.const(())
      case Plus(x,y)   => IR.plus(eval(x),eval(y))
      case Less(x,y)   => IR.less(eval(x),eval(y))
      case New(x) => 
        val a = IR.pair(IR.const(x),itvec)
        store = IR.update(store, a, IR.const(Map()))
        a
      case Get(x, f) => 
        IR.select(IR.select(store, eval(x)), IR.const(f))
      case Put(x, f, y) => 
        val a = eval(x)
        val old = IR.select(store, a)
        store = IR.update(store, a, IR.update(old, IR.const(f), eval(y)))
        IR.const(())
      case If(c,a,b) => 
        val c1 = eval(c)
        //if (!mayZero(c1)) eval(a) else if (mustZero(c1)) eval(b) else {
          val save = store
          //assert(c1)
          val e1 = eval(a)
          val s1 = store
          store = save
          //assertNot(c1)
          val e2 = eval(b)
          val s2 = store
          store = IR.iff(c1,s1,s2)
          IR.iff(c1,e1,e2)
        //}
      case While(c,b) =>  

        /*{println(s"def loop(store0, n0) = {")
        val savest = store
        store = "store0"
        val saveit = itvec
        itvec = itvec+"::n0"
        val c0x = eval(c)
        eval(b)
        println(s"if ($c0x) loop($store,n0+1) else (store0,n0)")
        println("}")
        store = savest
        itvec = saveit
        val n = reflect(s"loop($store,0)._2 // count")}*/

        val loop = GRef(freshVar)
        val n0 = GRef(freshVar)



          val savevc = varCount
          val savest = store
          val saveit = itvec

          // case i == 0
          store = savest
          itvec = IR.pair(itvec,IR.const(0))
          //val c0 = eval(c) to eval or not to eval?
          val afterC0 = store

          // case i > 0
          store = IR.call(loop,IR.plus(n0,IR.const(-1)))
          itvec = IR.pair(itvec,n0)

          val cX = eval(c)
          val afterCX = store
          eval(b)
          val afterBX = store
          val r0 = IR.iff(cX, afterBX, afterCX)
          val r = IR.iff(IR.less(IR.const(0),n0), r0, afterC0)

          store = savest
          itvec = saveit

        val xx = IR.fun(loop.toString, n0.toString, r)
        assert(xx === loop)

        val n = IR.fixindex(loop)
        store = IR.call(loop,n)

        eval(c) // once more (this one will fail)

        // TODO: fixpoint

        IR.const(())

      case Block(Nil) => IR.const(())
      case Block(xs) => xs map eval reduceLeft ((a,b) => b)
    }


    // *** run and test

    def run(testProg: Exp) = {
      println("prog: " + testProg)
      store = store0
      itvec = itvec0
      varCount = varCount0
      globalDefs = globalDefs0
      val res = eval(testProg)
      println("res: " + res)
      println("store: " + store)
      val store2 = IR.iterateAll(store)
      println("transformed: " + store2)
      val sched = IR.schedule(store2)
      println("sched:")
      sched.foreach(IR.printStm)

      //store.printBounds
      println("----")
    }

    // test some integer computations

    val testProg1 = Block(List(
      Assign("i", Const(0)),
      Assign("y", Const(0)),
      Assign("x", Const(8)),
      While(Less(Ref("i"),Const(100)), Block(List(
        Assign("x", Const(7)),
        Assign("x", Plus(Ref("x"), Const(1))),
        Assign("y", Plus(Ref("y"), Const(1))), // TOOD: how to relate to loop var??
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    val testProg2 = Block(List(
      Assign("x", Const(900)), // input
      Assign("y", Const(0)),
      Assign("z", Const(0)),
      While(Less(Const(0), Ref("x")), Block(List(
        Assign("z", Plus(Ref("z"), Ref("x"))),
        If(Less(Ref("y"),Const(17)), 
          Block(List(
            Assign("y", Plus(Ref("y"), Const(1)))
          )),
          Block(Nil)
        ),
        Assign("x", Plus(Ref("x"), Const(-1)))
      ))),
      Assign("r", Ref("x"))
    ))

    // test store logic

    val testProg3 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Assign("y", New("B")),
        Put(Ref("y"), "head", Ref("i")),
        Put(Ref("y"), "tail", Ref("x")),
        Assign("x", Ref("y")),
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    val testProg4 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      Assign("y", New("B")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Put(Ref("y"), "head", Ref("i")),
        Put(Ref("y"), "tail", Ref("x")),
        Assign("x", Ref("y")),
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    val testProg5 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Put(Ref("x"), "head", Ref("i")),
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    // modify stuff after a loop

    val testProg6 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      Assign("y", New("B")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Put(Ref("y"), "head", Ref("i")),
        Put(Ref("y"), "tail", Ref("x")),
        Assign("x", Ref("y")),
        Assign("i", Plus(Ref("i"), Const(1)))
      ))),
      Put(Ref("y"), "tail", Ref("z")),
      Put(Ref("y"), "head", Const(7))
    ))

    // strong update for if

    val testProg7 = Block(List(
      Assign("x", New("A")),
      If(Direct(vref("input")),
        Block(List(
          Put(Ref("x"), "a", New("B")),
          Put(Get(Ref("x"), "a"), "foo", Const(5))
        )),
        Block(List(
          Put(Ref("x"), "a", New("C")),
          Put(Get(Ref("x"), "a"), "bar", Const(5))
        ))
      ),
      Assign("foo", Get(Get(Ref("x"), "a"), "foo")),
      Assign("bar", Get(Get(Ref("x"), "a"), "bar"))
    ))

    val testProg8 = Block(List(
      Assign("x", New("A")),
      Put(Ref("x"), "a", New("A2")),
      Put(Get(Ref("x"), "a"), "baz", Const(3)),
      If(Direct(vref("input")),
        Block(List(
          Put(Ref("x"), "a", New("B")), // strong update, overwrite
          Put(Get(Ref("x"), "a"), "foo", Const(5))
        )),
        Block(List(
          Put(Ref("x"), "a", New("C")), // strong update, overwrite
          Put(Get(Ref("x"), "a"), "bar", Const(5))
        ))
      ),
      Put(Get(Ref("x"), "a"), "bar", Const(7)), // this is not a strong update, because 1.a may be one of two allocs
      Assign("xbar", Get(Get(Ref("x"), "a"), "bar")) // should still yield 7!
    ))

    // update stuff allocated in a loop

    val testProg9 = Block(List(
      Assign("x", New("X")),
      Put(Ref("x"), "a", New("A")),
      Put(Get(Ref("x"), "a"), "baz", Const(3)),
      While(Direct(vref("input")),
        Block(List(
          Put(Ref("x"), "a", New("B")), // strong update, overwrite
          Put(Get(Ref("x"), "a"), "foo", Const(5))
        ))
      ),
      Put(Get(Ref("x"), "a"), "bar", Const(7)), // this is not a strong update, because 1.a may be one of two allocs
      Assign("xbar", Get(Get(Ref("x"), "a"), "bar")) // should still yield 7!
    ))

  }

  def testA = withOutFileChecked(prefix+"A") {
    Test1.run(Test1.testProg1)
    Test1.run(Test1.testProg2)
  }

  def testB = withOutFileChecked(prefix+"B") {
    Test1.run(Test1.testProg3)
    Test1.run(Test1.testProg4) // 3 and 4 should be different: alloc within the loop vs before
    Test1.run(Test1.testProg5)
  }
  def testC = withOutFileChecked(prefix+"C") {
    Test1.run(Test1.testProg6)
  }
  def testD = withOutFileChecked(prefix+"D") {
    Test1.run(Test1.testProg7)
    Test1.run(Test1.testProg8)
  }
  def testE = withOutFileChecked(prefix+"E") {
    Test1.run(Test1.testProg9)
  }



}