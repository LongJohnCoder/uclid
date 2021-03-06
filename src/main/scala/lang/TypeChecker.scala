/*
 * UCLID5 Verification and Synthesis Engine
 *
 * Copyright (c) 2017.
 * Sanjit A. Seshia, Rohit Sinha and Pramod Subramanyan.
 *
 * All Rights Reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 *
 * documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Authors: Norman Mu, Pramod Subramanyan
 *
 * All sorts of type inference and type checking functionality is in here.
 *
 */

package uclid
package lang

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.mutable.{Set => MutableSet}
import scala.collection.immutable.{Map => ImmutableMap}
import scala.util.parsing.input.Position
import com.typesafe.scalalogging.Logger

class TypeSynonymFinderPass extends ReadOnlyPass[Unit]
{
  type TypeMap = MutableMap[Identifier, Type]
  type TypeSet = MutableSet[Identifier]
  var typeDeclMap : TypeMap = MutableMap.empty
  var typeSynonyms : TypeSet = MutableSet.empty

  override def reset () {
    typeDeclMap.clear()
    typeSynonyms.clear()
  }
  override def applyOnModule( d : TraversalDirection.T, module : Module, in : Unit, context : Scope) : Unit = {
    if (d == TraversalDirection.Up) {
      validateSynonyms(module)
      simplifySynonyms(module)
    }
  }
  override def applyOnTypeDecl(d : TraversalDirection.T, typDec : TypeDecl, in : Unit, context : Scope) : Unit = {
    if (d == TraversalDirection.Down) {
      typeDeclMap.put(typDec.id, typDec.typ)
    }
  }
  override def applyOnType( d : TraversalDirection.T, typ : Type, in : Unit, context : Scope) : Unit = {
    if (d == TraversalDirection.Down) {
      typ match {
        case SynonymType(name) => typeSynonyms += name
        case _ => /* skip */
      }
    }
  }

  def validateSynonyms(module : Module) {
    typeSynonyms.foreach {
      (syn) => Utils.checkParsingError(
          typeDeclMap.contains(syn),
          "Type synonym '" + syn.toString + "' used without declaration in module '" + module.id + "'",
          syn.pos, module.filename
      )
    }
  }

  def simplifyType(typ : Type, visited : Set[Identifier], m : Module) : Type = {
    typ match {
      case SynonymType(otherName) =>
        if (visited contains otherName) {
          val msg = "Recursively defined synonym type: %s".format(otherName.toString)
          throw new Utils.TypeError(msg, Some(otherName.pos), m.filename)
        }
        simplifyType(typeDeclMap.get(otherName).get, visited + otherName, m)
      case TupleType(fieldTypes) =>
        TupleType(fieldTypes.map(simplifyType(_, visited, m)))
      case RecordType(fields) =>
        RecordType(fields.map((f) => (f._1, simplifyType(f._2, visited, m))))
      case MapType(inTypes, outType) =>
        MapType(inTypes.map(simplifyType(_, visited, m)), simplifyType(outType, visited, m))
      case ArrayType(inTypes, outType) =>
        ArrayType(inTypes.map(simplifyType(_, visited, m)), simplifyType(outType, visited, m))
      case _ =>
        typ
    }
  }

  def simplifySynonyms(m : Module) {
    var simplified = false
    do {
      simplified = false
      typeDeclMap.foreach {
        case (name, typ) => {
          typ match {
            case SynonymType(otherName) =>
                simplified = true
                typeDeclMap.put(name, simplifyType(typeDeclMap.get(otherName).get, Set.empty, m))
            case _ =>
                typeDeclMap.put(name, simplifyType(typ, Set.empty, m))
          }
        }
      }
    } while (simplified)
  }
}

class TypeSynonymFinder extends ASTAnalyzer("TypeSynonymFinder", new TypeSynonymFinderPass())  {
  override def pass = super.pass.asInstanceOf[TypeSynonymFinderPass]
  in = Some(Unit)
}

class TypeSynonymRewriterPass extends RewritePass {
  lazy val manager : PassManager = analysis.manager
  lazy val typeSynonymFinderPass = manager.pass("TypeSynonymFinder").asInstanceOf[TypeSynonymFinder].pass
  override def rewriteType(typ : Type, ctx : Scope) : Option[Type] = {
    val result = typ match {
      case SynonymType(name) => typeSynonymFinderPass.typeDeclMap.get(name)
      case _ => Some(typ)
    }
    return result
  }
}

class TypeSynonymRewriter extends ASTRewriter(
    "TypeSynonymRewriter", new TypeSynonymRewriterPass())

object ReplacePolymorphicOperators {
  def toInt(op : PolymorphicOperator) : IntArgOperator = {
    val intOp = op match {
      case LTOp() => IntLTOp()
      case LEOp() => IntLEOp()
      case GTOp() => IntGTOp()
      case GEOp() => IntGEOp()
      case AddOp() => IntAddOp()
      case SubOp() => IntSubOp()
      case MulOp() => IntMulOp()
      case UnaryMinusOp() => IntUnaryMinusOp()
    }
    intOp.pos = op.pos
    intOp
  }
  def toBitvector(op : PolymorphicOperator, w : Int) : BVArgOperator = {
    val bvOp = op match {
      case LTOp() => BVLTOp(w)
      case LEOp() => BVLEOp(w)
      case GTOp() => BVGTOp(w)
      case GEOp() => BVGEOp(w)
      case AddOp() => BVAddOp(w)
      case SubOp() => BVSubOp(w)
      case MulOp() => BVMulOp(w)
      case UnaryMinusOp() => BVUnaryMinusOp(w)
    }
    bvOp.pos = op.pos
    bvOp
  }
  def toType(op : PolymorphicOperator, typ : NumericType) = {
    typ match {
      case intTyp : IntegerType => toInt(op)
      case bvTyp : BitVectorType => toBitvector(op, bvTyp.width)
    }
  }
  def rewrite(e : Expr, typ : NumericType) : Expr = {
    def r(e : Expr) : Expr = rewrite(e, typ)
    def rs(es : List[Expr])  = es.map(r(_))

    e match {
      case i : Identifier => e
      case ei : ExternalIdentifier => e
      case l : Literal => e
      case Tuple(es) => Tuple(es.map(r(_)))
      case OperatorApplication(op, operands) =>
        val opP = op match {
          case p : PolymorphicOperator => toType(p, typ)
          case _ => op
        }
        OperatorApplication(opP, rs(operands))
      case ArraySelectOperation(expr, indices) =>
        ArraySelectOperation(r(expr), rs(indices))
      case ArrayStoreOperation(expr, indices, value) =>
        ArrayStoreOperation(r(expr), rs(indices), r(value))
      case FuncApplication(expr, args) =>
        FuncApplication(r(expr), rs(args))
      case Lambda(args, expr) =>
        Lambda(args, r(expr))
    }
  }
}


class ExpressionTypeCheckerPass extends ReadOnlyPass[Set[Utils.TypeError]]
{
  lazy val logger = Logger(classOf[ExpressionTypeCheckerPass])
  type Memo = MutableMap[IdGenerator.Id, Type]
  var memo : Memo = MutableMap.empty
  type ErrorList = Set[Utils.TypeError]

  var polyOpMap : MutableMap[IdGenerator.Id, Operator] = MutableMap.empty
  var bvOpMap : MutableMap[IdGenerator.Id, Int] = MutableMap.empty

  override def reset() = {
    memo.clear()
    polyOpMap.clear()
  }

  override def applyOnExpr(d : TraversalDirection.T, e : Expr, in : ErrorList, ctx : Scope) : ErrorList = {
    if (d == TraversalDirection.Up) {
      try {
        typeOf(e, ctx)
      } catch {
        case p : Utils.TypeError => {
          return (in + p)
        }
      }
    }
    return in
  }

  override def applyOnLHS(d : TraversalDirection.T, lhs : Lhs, in : ErrorList, ctx : Scope) : ErrorList = {
    if (d == TraversalDirection.Up) {
      try {
        typeOf(lhs, ctx)
      } catch {
        case p : Utils.TypeError => {
          return (in + p)
        }
      }
    }
    return in
  }

  def raiseTypeError(msg: => String, pos: => Position, filename: => Option[String]) {
      throw new Utils.TypeError(msg, Some(pos), filename)
  }
  def checkTypeError(condition: Boolean, msg: => String, pos: => Position, filename: => Option[String]) {
    if (!condition) {
      raiseTypeError(msg, pos, filename)
    }
  }

  def typeOf(lhs : Lhs, c : Scope) : Type = {
    val cachedType = memo.get(lhs.astNodeId)
    if (cachedType.isDefined) {
      return cachedType.get
    }

    val id = lhs.ident
    val typOpt = c.typeOf(id)
    checkTypeError(typOpt.isDefined, "Unknown variable in LHS: " + id.toString, lhs.pos, c.filename)
    val typ = typOpt.get
    val resultType = lhs match {
      case LhsId(_) | LhsNextId(_) =>
        typ
      case LhsArraySelect(id, indices) =>
        checkTypeError(typ.isArray, "Lhs variable in array index operation must be of type array: " + id.toString, lhs.pos, c.filename)
        val indexTypes = indices.map(typeOf(_, c))
        val arrTyp = typ.asInstanceOf[ArrayType]
        lazy val indexTypString = "(" + Utils.join(indexTypes.map(_.toString), ", ") + ")"
        lazy val inTypString = "(" + Utils.join(arrTyp.inTypes.map(_.toString), ", ") + ")"
        checkTypeError(arrTyp.inTypes == indexTypes, "Invalid index types. Expected: " + inTypString + "; got: " + indexTypString, lhs.pos, c.filename)
        arrTyp.outType
      case LhsRecordSelect(id, fields) =>
        checkTypeError(typ.isProduct, "Lhs variable in record select operation must be a product type: " + id.toString, lhs.pos, c.filename)
        val prodType = typ.asInstanceOf[ProductType]
        val fieldType = prodType.nestedFieldType(fields)
        lazy val fieldTypeString = Utils.join(fields.map(_.toString), ".")
        checkTypeError(fieldType.isDefined, "Field does not exist: " + fieldTypeString, lhs.pos, c.filename)
        fieldType.get
      case LhsSliceSelect(id, slice) =>
        checkTypeError(typ.isBitVector, "Lhs variable in bitvector slice update must be a bitvector: " + id.toString, lhs.pos, c.filename)
        val bvType = typ.asInstanceOf[BitVectorType]
        checkTypeError(bvType.isValidSlice(slice), "Invalid slice: " + slice.toString, slice.pos, c.filename)
        BitVectorType(slice.width.get)
      case LhsVarSliceSelect(id, slice) =>
        checkTypeError(slice.width.isDefined, "Width of bitvector slice is not constant", lhs.pos, c.filename)
        BitVectorType(slice.width.get)
    }
    memo.put(lhs.astNodeId, resultType)
    return resultType
  }
  def typeOf(e : Expr, c : Scope) : Type = {
    logger.debug("e: {}", e.toString())
    logger.debug("c: {}", c.map.keys.toString())
    def polyResultType(op : PolymorphicOperator, argType : Type) : Type = {
      op match {
        case LTOp() | LEOp() | GTOp() | GEOp() => new BooleanType()
        case AddOp() | SubOp() | MulOp() | UnaryMinusOp() => argType
      }
    }
    def opAppType(opapp : OperatorApplication) : Type = {
      val argTypes = opapp.operands.map(typeOf(_, c + opapp))
      logger.debug("opAppType: {}", opapp.toString())
      opapp.op match {
        case polyOp : PolymorphicOperator => {
          def numArgs(op : PolymorphicOperator) : Int = {
            op match {
              case UnaryMinusOp() => 1
              case _ => 2
            }
          }
          val nArgs = numArgs(polyOp)
          checkTypeError(argTypes.size == nArgs, "Operator '" + opapp.op.toString + "' must have two arguments", opapp.pos, c.filename)
          if (nArgs > 1) {
            checkTypeError(argTypes(0) == argTypes(1),
                "Arguments to operator '" + opapp.op.toString + "' must be of the same type. Types of expression '" +
                opapp.toString() + "' are " + argTypes(0).toString() + " and " + argTypes(1).toString(),
                opapp.pos, c.filename)
          }
          checkTypeError(argTypes.forall(_.isNumeric), "Arguments to operator '" + opapp.op.toString + "' must be of a numeric type", opapp.pos, c.filename)
          typeOf(opapp.operands(0), c) match {
            case i : IntegerType =>
              polyOpMap.put(polyOp.astNodeId, ReplacePolymorphicOperators.toInt(polyOp))
              polyResultType(polyOp, i)
            case bv : BitVectorType =>
              polyOpMap.put(polyOp.astNodeId, ReplacePolymorphicOperators.toBitvector(polyOp, bv.width))
              polyResultType(polyOp, bv)
            case _ => throw new Utils.UnimplementedException("Unknown operand type to polymorphic operator '" + opapp.op.toString + "'")
          }
        }
        case intOp : IntArgOperator => {
          def numArgs(op : IntArgOperator) : Int = {
            op match {
              case IntUnaryMinusOp() => 1
              case _ => 2
            }
          }
          checkTypeError(argTypes.size == numArgs(intOp), "Operator '" + opapp.op.toString + "' must have two arguments", opapp.pos, c.filename)
          checkTypeError(argTypes.forall(_.isInstanceOf[IntegerType]), "Arguments to operator '" + opapp.op.toString + "' must be of type Integer", opapp.pos, c.filename)
          intOp match {
            case IntLTOp() | IntLEOp() | IntGTOp() | IntGEOp() => new BooleanType()
            case IntAddOp() | IntSubOp() | IntMulOp() | IntUnaryMinusOp() => new IntegerType()
          }
        }
        case bvOp : BVArgOperator => {
          checkTypeError(argTypes.size == bvOp.arity, "Operator '%s' must have exactly %d argument(s)".format(opapp.op.toString, bvOp.arity), opapp.pos, c.filename)
          checkTypeError(argTypes.forall(_.isInstanceOf[BitVectorType]), "Argument(s) to operator '" + opapp.op.toString + "' must be of type BitVector", opapp.pos, c.filename)
          bvOp match {
            case BVLTOp(_) | BVLEOp(_) | BVGTOp(_) | BVGEOp(_) =>
              new BooleanType()
            case BVAddOp(_) | BVSubOp(_) | BVMulOp(_) | BVUnaryMinusOp(_) =>
              checkTypeError(bvOp.w != 0, "Invalid width argument to '%s' operator".format(opapp.op.toString()), opapp.pos, c.filename)
              new BitVectorType(bvOp.w)
            case BVAndOp(_) | BVOrOp(_) | BVXorOp(_) | BVNotOp(_) =>
              val t = BitVectorType(argTypes(0).asInstanceOf[BitVectorType].width)
              bvOpMap.put(bvOp.astNodeId, t.width)
              t
            case BVSignExtOp(w, e) =>
              checkTypeError(e > 0, "Invalid width argument to '%s' operator".format(opapp.op.toString()), opapp.pos, c.filename)
              val w = e + argTypes(0).asInstanceOf[BitVectorType].width
              bvOpMap.put(bvOp.astNodeId, w)
              BitVectorType(w)
            case BVZeroExtOp(w, e) =>
              checkTypeError(e > 0, "Invalid width argument to '%s' operator".format(opapp.op.toString()), opapp.pos, c.filename)
              val w = e + argTypes(0).asInstanceOf[BitVectorType].width
              bvOpMap.put(bvOp.astNodeId, w)
              BitVectorType(w)
            case BVLeftShiftOp(w, e) =>
              checkTypeError(e > 0, "Invalid width argument to '%s' operator".format(opapp.op.toString()), opapp.pos, c.filename)
              val w = argTypes(0).asInstanceOf[BitVectorType].width
              bvOpMap.put(bvOp.astNodeId, w)
              BitVectorType(w)
            case BVLRightShiftOp(w, e) =>
              checkTypeError(e > 0, "Invalid width argument to '%s' operator".format(opapp.op.toString()), opapp.pos, c.filename)
              val w = argTypes(0).asInstanceOf[BitVectorType].width
              bvOpMap.put(bvOp.astNodeId, w)
              BitVectorType(w)
            case BVARightShiftOp(w, e) =>
              checkTypeError(e > 0, "Invalid width argument to '%s' operator".format(opapp.op.toString()), opapp.pos, c.filename)
              val w = argTypes(0).asInstanceOf[BitVectorType].width
              bvOpMap.put(bvOp.astNodeId, w)
              BitVectorType(w)
          }
        }
        case qOp : QuantifiedBooleanOperator => {
          checkTypeError(argTypes(0).isInstanceOf[BooleanType], "Operand to the quantifier '" + qOp.toString + "' must be boolean", opapp.pos, c.filename)
          BooleanType()
        }
        case boolOp : BooleanOperator => {
          boolOp match {
            case NegationOp() =>
              checkTypeError(argTypes.size == 1, "Operator '" + opapp.op.toString + "' must have one argument", opapp.pos, c.filename)
              checkTypeError(argTypes.forall(_.isInstanceOf[BooleanType]), "Arguments to operator '" + opapp.op.toString + "' must be of type Bool", opapp.pos, c.filename)
            case _ =>
              checkTypeError(argTypes.size == 2, "Operator '" + opapp.op.toString + "' must have two arguments", opapp.pos, c.filename)
              checkTypeError(argTypes.forall(_.isInstanceOf[BooleanType]), "Arguments to operator '" + opapp.op.toString + "' must be of type Bool", opapp.pos, c.filename)
          }
          BooleanType()
        }
        case cmpOp : ComparisonOperator => {
          checkTypeError(argTypes.size == 2, "Operator '" + opapp.op.toString + "' must have two arguments", opapp.pos, c.filename)
          checkTypeError(argTypes(0) == argTypes(1), "Arguments to operator '" + opapp.op.toString + "' must be of the same type", opapp.pos, c.filename)
          BooleanType()
        }
        case tOp : TemporalOperator => BooleanType()
        case extrOp : ExtractOp => {
          extrOp match {
            case ConstExtractOp(slice) => {
              checkTypeError(argTypes.size == 1, "Operator '" + opapp.op.toString + "' must have one argument", opapp.pos, c.filename)
              checkTypeError(argTypes(0).isInstanceOf[BitVectorType], "Operand to operator '" + opapp.op.toString + "' must be of type BitVector", opapp.pos, c.filename)
              checkTypeError(argTypes(0).asInstanceOf[BitVectorType].width > slice.hi, "Operand to operator '" + opapp.op.toString + "' must have width > "  + slice.hi.toString, opapp.pos, c.filename)
              checkTypeError(slice.hi >= slice.lo, "High-operand must be greater than or equal to low operand for operator '" + opapp.op.toString + "'", opapp.pos, c.filename)
              checkTypeError(slice.hi >= 0, "Operand to operator '" + opapp.op.toString + "' must be non-negative", opapp.pos, c.filename)
              checkTypeError(slice.lo >= 0, "Operand to operator '" + opapp.op.toString + "' must be non-negative", opapp.pos, c.filename)
              BitVectorType(slice.hi - slice.lo + 1)
            }
            case VarExtractOp(slice) => {
              checkTypeError(slice.width.isDefined, "Slice width is not constant", extrOp.pos, c.filename)
              BitVectorType(slice.width.get)
            }
          }
        }
        case ConcatOp() => {
          checkTypeError(argTypes.size == 2, "Operator '" + opapp.op.toString + "' must have two arguments", opapp.pos, c.filename)
          checkTypeError(argTypes.forall(_.isInstanceOf[BitVectorType]), "Arguments to operator '" + opapp.op.toString + "' must be of type BitVector", opapp.pos, c.filename)
          new BitVectorType(argTypes(0).asInstanceOf[BitVectorType].width + argTypes(1).asInstanceOf[BitVectorType].width)
        }
        case PolymorphicSelect(field) =>
          Utils.assert(argTypes.size == 1, "Select operator must have exactly one operand.")
          argTypes(0) match {
            case recType : RecordType =>
              val typOption = recType.fieldType(field)
              checkTypeError(!typOption.isEmpty, "Field '" + field.toString + "' does not exist in " + recType.toString(), opapp.pos, c.filename)
              polyOpMap.put(opapp.op.astNodeId, RecordSelect(field))
              typOption.get
            case tupType : TupleType =>
              val indexS = field.name.substring(1)
              checkTypeError(indexS.forall(Character.isDigit), "Tuple fields must be integers preceded by an underscore", opapp.pos, c.filename)
              val indexI = indexS.toInt
              checkTypeError(indexI >= 1 && indexI <= tupType.numFields, "Invalid tuple index: " + indexS, opapp.pos, c.filename)
              polyOpMap.put(opapp.op.astNodeId, RecordSelect(field))
              tupType.fieldTypes(indexI-1)
            case modT : ModuleType =>
              val fldT = modT.typeMap.get(field)
              checkTypeError(fldT.isDefined, "Unknown type for selection: %s".format(field.toString), field.pos, c.filename)
              polyOpMap.put(opapp.op.astNodeId, SelectFromInstance(field))
              fldT.get
            case _ =>
              checkTypeError(false, "Argument to select operator must be of type record or instance", opapp.pos, c.filename)
              new UndefinedType()
          }
        case RecordSelect(field) =>
          Utils.assert(argTypes.size == 1, "Select operator must have exactly one operand.")
          argTypes(0) match {
            case recType : RecordType =>
              val typOption = recType.fieldType(field)
              checkTypeError(!typOption.isEmpty, "Field '" + field.toString + "' does not exist in " + recType.toString(), opapp.pos, c.filename)
              typOption.get
            case tupType : TupleType =>
              val indexS = field.name.substring(1)
              checkTypeError(indexS.forall(Character.isDigit), "Tuple fields must be integers preceded by an underscore", opapp.pos, c.filename)
              val indexI = indexS.toInt
              checkTypeError(indexI >= 1 && indexI <= tupType.numFields, "Invalid tuple index: " + indexS, opapp.pos, c.filename)
              tupType.fieldTypes(indexI-1)
            case _ =>
              checkTypeError(false, "Argument to select operator must be of type record", opapp.pos, c.filename)
              new UndefinedType()
          }
        case SelectFromInstance(field) =>
          Utils.assert(argTypes.size == 1, "Select operator must have exactly one operand.")
          val inst= argTypes(0)
          checkTypeError(inst.isInstanceOf[ModuleType], "Argument to select operator must be module instance", field.pos, c.filename)
          val modT = inst.asInstanceOf[ModuleType]
          val fldT = modT.typeMap.get(field)
          checkTypeError(fldT.isDefined, "Unknown type for selection: %s".format(field.toString), field.pos, c.filename)
          fldT.get
        case GetNextValueOp() =>
          Utils.assert(argTypes.size == 1, "Expected exactly one argument to GetFinalValue")
          argTypes(0)
        case OldOperator() =>
          checkTypeError(argTypes.size == 1, "Expect exactly one argument to 'old'", opapp.pos, c.filename)
          checkTypeError(opapp.operands(0).isInstanceOf[Identifier], "Argument to old operator must be an identifier", opapp.pos, c.filename)
          argTypes(0)
        case PastOperator() =>
          checkTypeError(argTypes.size == 1, "Expect exactly on argument to 'past'", opapp.pos, c.filename)
          checkTypeError(opapp.operands(0).isInstanceOf[Identifier], "Argument to past operator must be an identifier", opapp.pos, c.filename)
          argTypes(0)
        case HistoryOperator() =>
          checkTypeError(argTypes.size == 2, "Expect exactly two arguments to 'history'", opapp.pos, c.filename)
          checkTypeError(opapp.operands(0).isInstanceOf[Identifier], "First argument to history operator must be an identifier", opapp.pos, c.filename)
          checkTypeError(opapp.operands(1).isInstanceOf[IntLit], "Second argument to history must be an integer literal", opapp.pos, c.filename)
          val historyIndex = opapp.operands(1).asInstanceOf[IntLit].value
          checkTypeError(historyIndex > 0, "History index must be non-negative", opapp.pos, c.filename)
          checkTypeError(historyIndex.toInt < 65536, "History index is too large", opapp.pos, c.filename)
          argTypes(0)
        case ITEOp() =>
          checkTypeError(argTypes.size == 3, "Expect exactly three arguments to if-then-else expressions", opapp.pos, c.filename)
          checkTypeError(argTypes(0).isBool, "Condition in if-then-else must be boolean", opapp.pos, c.filename)
          checkTypeError(argTypes(1) == argTypes(2), "Then- and else- expressions in an if-then-else must be of the same type", opapp.pos, c.filename)
          argTypes(1)
        case DistinctOp() =>
          checkTypeError(argTypes.size >= 2, "Expect 2 or more arguments to the distinct operator", opapp.pos, c.filename)
          checkTypeError(argTypes.forall(t => t == argTypes(0)), "All arguments to distinct operator must be the same", opapp.pos, c.filename)
          BooleanType()
      }
    }

    def arraySelectType(arrSel : ArraySelectOperation) : Type = {
      checkTypeError(typeOf(arrSel.e, c).isInstanceOf[ArrayType], "Type error in the array operand of select operation", arrSel.pos, c.filename)
      val indTypes = arrSel.index.map(typeOf(_, c))
      val arrayType = typeOf(arrSel.e, c).asInstanceOf[ArrayType]
      lazy val message = "Array index type error. Expected: (" +
                          Utils.join(arrayType.inTypes.map((t) => t.toString), ", ") + "). Got: (" +
                          Utils.join(indTypes.map((t) => t.toString), ", ") + ")"
      checkTypeError(arrayType.inTypes == indTypes, message, arrSel.pos, c.filename)
      return arrayType.outType
    }

    def arrayStoreType(arrStore : ArrayStoreOperation) : Type = {
      checkTypeError(typeOf(arrStore.e, c).isInstanceOf[ArrayType], "Type error in the array operand of store operation", arrStore.pos, c.filename)
      val indTypes = arrStore.index.map(typeOf(_, c))
      val valueType = typeOf(arrStore.value, c)
      val arrayType = typeOf(arrStore.e, c).asInstanceOf[ArrayType]
      checkTypeError(arrayType.inTypes == indTypes, "Array index type error", arrStore.pos, c.filename)
      checkTypeError(arrayType.outType == valueType, "Array update value type error", arrStore.pos, c.filename)
      return arrayType
    }

    def funcAppType(fapp : FuncApplication) : Type = {
      val funcType1 = typeOf(fapp.e, c)
      lazy val typeErrorMsg = "Cannot apply %s, which is of type %s".format(fapp.e.toString, funcType1.toString)
      checkTypeError(funcType1.isInstanceOf[MapType], typeErrorMsg, fapp.pos, c.filename)
      val funcType = funcType1.asInstanceOf[MapType]
      val argTypes = fapp.args.map(typeOf(_, c))
      checkTypeError(funcType.inTypes == argTypes, "Argument type error in application", fapp.pos, c.filename)
      return funcType.outType
    }

    def lambdaType(lambda : Lambda) : Type = {
      return MapType(lambda.ids.map(_._2), typeOf(lambda.e, c))
    }

    val cachedType = memo.get(e.astNodeId)
    if (cachedType.isEmpty) {
      val typ = e match {
        case i : Identifier =>
          val knownId = c.typeOf(i).isDefined
          checkTypeError(knownId, "Unknown identifier: %s".format(i.name), i.pos, c.filename)
          (c.typeOf(i).get)
        case eId : ExternalIdentifier =>
          val mId = eId.moduleId
          val fId = eId.id
          val moduleTypeOption = c.typeOf(mId)
          checkTypeError(moduleTypeOption.isDefined, "Unknown module: %s".format(mId.toString), mId.pos, c.filename)
          val moduleTypeP = moduleTypeOption.get
          checkTypeError(moduleTypeP.isInstanceOf[ModuleType], "Identifier '%s' is not a module".format(mId.toString), mId.pos, c.filename)
          val moduleType = moduleTypeP.asInstanceOf[ModuleType]
          moduleType.externalTypeMap.get(fId) match {
            case None =>
              raiseTypeError("Unknown external '%s' in module '%s'".format(fId.toString, mId.toString), fId.pos, c.filename)
              UndefinedType()
            case Some(typ) => typ
          }
        case f : FreshLit => f.typ
        case b : BoolLit => BooleanType()
        case i : IntLit => IntegerType()
        case s : StringLit => StringType()
        case bv : BitVectorLit => BitVectorType(bv.width)
        case a : ConstArrayLit =>
          val valTyp = typeOf(a.value, c)
          a.typ match {
            case ArrayType(inTyps, outTyp) =>
              checkTypeError(outTyp == valTyp, "Array type does not match literal type", a.value.pos, c.filename)
              a.typ
            case _ =>
              raiseTypeError("Expected an array type", a.typ.pos, c.filename)
              UndefinedType()
          }
        case r : Tuple => new TupleType(r.values.map(typeOf(_, c)))
        case opapp : OperatorApplication => opAppType(opapp)
        case arrSel : ArraySelectOperation => arraySelectType(arrSel)
        case arrStore : ArrayStoreOperation => arrayStoreType(arrStore)
        case fapp : FuncApplication => funcAppType(fapp)
        case lambda : Lambda => lambdaType(lambda)
      }
      memo.put(e.astNodeId, typ)
      return typ
    } else {
      return cachedType.get
    }
  }
}

class ExpressionTypeChecker extends ASTAnalyzer("ExpressionTypeChecker", new ExpressionTypeCheckerPass())  {
  override def pass = super.pass.asInstanceOf[ExpressionTypeCheckerPass]
  override def visit(module : Module, context : Scope) : Option[Module] = {
    val errors = visitModule(module, Set.empty[Utils.TypeError], context)
    if (errors.size > 0) {
      throw new Utils.TypeErrorList(errors.toList)
    }
    return Some(module)
  }
}

class PolymorphicTypeRewriterPass extends RewritePass {
  lazy val manager : PassManager = analysis.manager
  lazy val typeCheckerPass = manager.pass("ExpressionTypeChecker").asInstanceOf[ExpressionTypeChecker].pass
  override def rewriteOperator(op : Operator, ctx : Scope) : Option[Operator] = {

    lazy val mappedOp = {
      val reifiedOp = typeCheckerPass.polyOpMap.get(op.astNodeId)
      Utils.assert(reifiedOp.isDefined, "No reified operator available for: " + op.toString)
      assert (reifiedOp.get.pos == op.pos)
      reifiedOp
    }
    op match {
      case p : PolymorphicOperator=>
        mappedOp
      case PolymorphicSelect(_) =>
        mappedOp
      case bv : BVArgOperator => {
        if (bv.w == 0) {
          val width = typeCheckerPass.bvOpMap.get(bv.astNodeId)
          Utils.assert(width.isDefined, "No width available for: " + bv.toString)
          val newOp = bv match {
            case BVAndOp(_) => width.flatMap((w) => Some(BVAndOp(w)))
            case BVOrOp(_) => width.flatMap((w) => Some(BVOrOp(w)))
            case BVXorOp(_) => width.flatMap((w) => Some(BVXorOp(w)))
            case BVNotOp(_) => width.flatMap((w) => Some(BVNotOp(w)))
            case BVSignExtOp(_, e) => width.flatMap((w) => Some(BVSignExtOp(w, e)))
            case BVZeroExtOp(_, e) => width.flatMap((w) => Some(BVZeroExtOp(w, e)))
            case BVLeftShiftOp(_, e) => width.flatMap((w) => Some(BVLeftShiftOp(w, e)))
            case BVLRightShiftOp(_, e) => width.flatMap((w) => Some(BVLRightShiftOp(w, e)))
            case BVARightShiftOp(_, e) => width.flatMap((w) => Some(BVARightShiftOp(w, e)))
            case _ => Some(bv)
          }
          newOp match {
            case Some(newOp) =>
              val opWithPos = newOp
              opWithPos.pos = bv.pos
              Some(opWithPos)
            case None =>
              // should never get here.
              None
          }
        } else {
          Some(bv)
        }
      }
      case _ => Some(op)
    }
  }
}
class PolymorphicTypeRewriter extends ASTRewriter(
    "PolymorphicTypeRewriter", new PolymorphicTypeRewriterPass())

