package pipedsl.typechecker

import pipedsl.common.Syntax._
import pipedsl.common.Security._
import pipedsl.common.Errors._
import pipedsl.typechecker.Environments.Environment
import pipedsl.typechecker.TypeChecker.TypeChecks

object IFCTypeChecker extends TypeChecks[Id, Type] {

  override def emptyEnv(): Environment[Id, Type] = Environments.EmptyTypeEnv

  override def checkExt(e: ExternDef, env: Environment[Id, Type]): Environment[Id, Type] = env

  override def checkFunc(f: FuncDef, env: Environment[Id, Type]): Environment[Id, Type] = {
    // Add arguments to environment
    val fenv = f.args.foldLeft(env)((e, p) => e.add(p.name, p.typ))
    checkCommand(f.body, fenv, LBottom)
    env
  }

  override def checkModule(m: ModuleDef, env: Environment[Id, Type]): Environment[Id, Type] = {
    // Add inputs and modules to environment
    val modEnv = m.inputs.foldLeft(env)((e, p) => e.add(p.name, p.typ))
    val fullEnv = m.modules.foldLeft(modEnv)((e, p) => e.add(p.name, p.typ))
    checkCommand(m.body, fullEnv, LBottom)
    env
  }

  override def checkCircuit(c: Circuit, env: Environment[Id, Type]): Environment[Id, Type] = env

  def checkCommand(c: Command, env: Environment[Id, Type], pc: Label): Unit = c match {
    case CSeq(c1, c2) =>
      checkCommand(c1, env, pc)
      checkCommand(c2, env, pc)

    case CTBar(c1, c2) =>
      checkCommand(c1, env, pc)
      checkCommand(c2, env, pc)

    case CIf(cond, cons, alt) =>
      val condLabel = checkExpression(cond, env)
      val newPc = pc.join(condLabel)
      checkCommand(cons, env, newPc)
      checkCommand(alt, env, newPc)

    case CSplit(cases, default) =>
      cases.foreach(c => {
        val condLabel = checkExpression(c.cond, env)
        val newPc = pc.join(condLabel)
        checkCommand(c.body, env, newPc)
      })
      checkCommand(default, env, pc)

    case CAssign(lhs, rhs) =>
      val lhsLabel = getLabel(lhs, env)
      val rhsLabel = checkExpression(rhs, env)

      // Explicit Flow: RHS must flow to LHS
      if (!rhsLabel.flowsTo(lhsLabel)) {
        throw ExplicitInformationFlow(rhs.pos, rhsLabel, lhsLabel)
      }

      // Implicit Flow: PC must flow to LHS
      if (!pc.flowsTo(lhsLabel)) {
        throw ImplicitInformationFlow(lhs.pos, pc, lhsLabel)
      }

    case CRecv(lhs, rhs) =>
      val lhsLabel = getLabel(lhs, env)
      val rhsLabel = checkExpression(rhs, env)

      if (!rhsLabel.flowsTo(lhsLabel)) {
        throw ExplicitInformationFlow(rhs.pos, rhsLabel, lhsLabel)
      }
      if (!pc.flowsTo(lhsLabel)) {
        throw ImplicitInformationFlow(lhs.pos, pc, lhsLabel)
      }

    case COutput(exp) =>
      checkExpression(exp, env)

    case CReturn(exp) =>
      checkExpression(exp, env)

    case CExpr(exp) =>
      checkExpression(exp, env)

    case _ => () // Ignore other commands for now
  }

  def checkExpression(e: Expr, env: Environment[Id, Type]): Label = e match {
    case EInt(_, _, _) => LBottom
    case EBool(_) => LBottom
    case EString(_) => LBottom

    case EVar(id) => getLabel(id, env)

    case EBinop(_, e1, e2) =>
      checkExpression(e1, env).join(checkExpression(e2, env))

    case EUop(_, ex) =>
      checkExpression(ex, env)

    case ETernary(cond, tval, fval) =>
      val l1 = checkExpression(cond, env)
      val l2 = checkExpression(tval, env)
      val l3 = checkExpression(fval, env)
      l1.join(l2).join(l3)

    case EMemAccess(mem, index, _, _, _, _) =>
      val memLabel = getLabel(mem, env)
      val idxLabel = checkExpression(index, env)
      // Reading from memory: result is join of memory label and index label
      // (Index label matters because it determines WHICH value we read)
      memLabel.join(idxLabel)

    case EBitExtract(num, _, _) =>
      checkExpression(num, env)

    case ECast(_, exp) =>
      checkExpression(exp, env)

    case EApp(_, args) =>
      args.map(a => checkExpression(a, env)).foldLeft[Label](LBottom)(_ join _)

    case ECall(_, _, args, _) =>
       args.map(a => checkExpression(a, env)).foldLeft[Label](LBottom)(_ join _)

    case _ => LBottom
  }

  private def getLabel(id: Id, env: Environment[Id, Type]): Label = {
    env.get(id) match {
      case Some(t) => t.lbl.getOrElse(LBottom)
      case None => id.typ match {
        case Some(t) => t.lbl.getOrElse(LBottom)
        case None => LBottom
      }
    }
  }

  private def getLabel(e: Expr, env: Environment[Id, Type]): Label = e match {
    case EVar(id) => getLabel(id, env)
    case EMemAccess(mem, _, _, _, _, _) => getLabel(mem, env)
    case _ => LBottom // Should not happen for LHS
  }

  // Custom Errors
  case class ExplicitInformationFlow(pos: scala.util.parsing.input.Position, from: Label, to: Label)
    extends RuntimeException(s"Explicit information flow violation at $pos. Cannot flow from $from to $to")

  case class ImplicitInformationFlow(pos: scala.util.parsing.input.Position, pc: Label, to: Label)
    extends RuntimeException(s"Implicit information flow violation at $pos. Context ($pc) cannot flow to $to")

}
