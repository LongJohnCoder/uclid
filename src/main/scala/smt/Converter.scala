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
 * Author: Pramod Subramanyan
 *
 * UCLID AST to SMT AST converter.
 *
 */
package uclid
package smt

object Converter {
  type SymbolTable = SymbolicSimulator.SymbolTable
  type FrameTable = SymbolicSimulator.FrameTable

  def typeToSMT(typ : lang.Type) : smt.Type = {
    typ match {
      case lang.UninterpretedType(id) =>
        smt.UninterpretedType(id.name)
      case lang.IntegerType() =>
        smt.IntType
      case lang.BooleanType() =>
        smt.BoolType
      case lang.StringType() =>
        throw new Utils.RuntimeError("String types cannot be converted.")
      case lang.BitVectorType(w) =>
        smt.BitVectorType(w)
      case lang.MapType(inTypes,outType) =>
        smt.MapType(inTypes.map(typeToSMT(_)), typeToSMT(outType))
      case lang.ArrayType(inTypes,outType) =>
        smt.ArrayType(inTypes.map(typeToSMT(_)), typeToSMT(outType))
      case lang.TupleType(argTypes) =>
        smt.TupleType(argTypes.map(typeToSMT(_)))
      case lang.RecordType(fields) =>
        smt.RecordType(fields.map((f) => (f._1.toString, typeToSMT(f._2))))
      case lang.EnumType(ids) =>
        smt.EnumType(ids.map(_.name))
      case lang.SynonymType(typ) =>
        throw new Utils.UnimplementedException("Synonym types must have been eliminated by now.")
      case lang.UndefinedType() | lang.ProcedureType(_, _) | lang.ExternalType(_, _) |
           lang.ModuleInstanceType(_) | lang.ModuleType(_, _, _, _, _, _, _, _) =>
        throw new AssertionError("Type '" + typ.toString + "' not expected here.")
    }
  }

  def smtToType(typ : smt.Type) : lang.Type = {
    typ match {
      case smt.UninterpretedType(name) =>
        lang.UninterpretedType(lang.Identifier(name))
      case smt.IntType =>
        lang.IntegerType()
      case smt.BoolType =>
        lang.BooleanType()
      case smt.BitVectorType(w) =>
        lang.BitVectorType(w)
      case smt.MapType(inTypes, outType) =>
        lang.MapType(inTypes.map(smtToType(_)), smtToType(outType))
      case smt.ArrayType(inTypes,outType) =>
        lang.ArrayType(inTypes.map(smtToType(_)), smtToType(outType))
      case smt.TupleType(argTypes) =>
        lang.TupleType(argTypes.map(smtToType(_)))
      case smt.RecordType(fields) =>
        lang.RecordType(fields.map((f) => (lang.Identifier(f._1), smtToType(f._2))))
      case smt.EnumType(ids) =>
        lang.EnumType(ids.map(lang.Identifier(_)))
      case _ =>
        throw new AssertionError("Type '" + typ.toString + "' not expected here.")
    }
  }

  def opToSMT(op : lang.Operator) : smt.Operator = {
    op match {
      // Integer operators.
      case lang.IntLTOp() => return smt.IntLTOp
      case lang.IntLEOp() => return smt.IntLEOp
      case lang.IntGTOp() => return smt.IntGTOp
      case lang.IntGEOp() => return smt.IntGEOp
      case lang.IntAddOp() => return smt.IntAddOp
      case lang.IntSubOp() => return smt.IntSubOp
      case lang.IntMulOp() => return smt.IntMulOp
      case lang.IntUnaryMinusOp() => return smt.IntSubOp
      // Bitvector operators.
      case lang.BVLTOp(w) => return smt.BVLTOp(w)
      case lang.BVLEOp(w) => return smt.BVLEOp(w)
      case lang.BVGTOp(w) => return smt.BVGTOp(w)
      case lang.BVGEOp(w) => return smt.BVGEOp(w)
      case lang.BVAddOp(w) => return smt.BVAddOp(w)
      case lang.BVSubOp(w) => return smt.BVSubOp(w)
      case lang.BVMulOp(w) => return smt.BVMulOp(w)
      case lang.BVUnaryMinusOp(w) => smt.BVMinusOp(w)
      case lang.BVAndOp(w) => return smt.BVAndOp(w)
      case lang.BVOrOp(w) => return smt.BVOrOp(w)
      case lang.BVXorOp(w) => return smt.BVXorOp(w)
      case lang.BVNotOp(w) => return smt.BVNotOp(w)
      case lang.ConstExtractOp(slice) => return smt.BVExtractOp(slice.hi, slice.lo)
      case lang.BVSignExtOp(w, e) => return smt.BVSignExtOp(w, e)
      case lang.BVZeroExtOp(w, e) => return smt.BVZeroExtOp(w, e)
      case lang.BVLeftShiftOp(w, e) => return smt.BVLeftShiftOp(w, e)
      case lang.BVLRightShiftOp(w, e) => return smt.BVLRightShiftOp(w, e)
      case lang.BVARightShiftOp(w, e) => return smt.BVARightShiftOp(w, e)
      // Boolean operators.
      case lang.ConjunctionOp() => return smt.ConjunctionOp
      case lang.DisjunctionOp() => return smt.DisjunctionOp
      case lang.IffOp() => return smt.IffOp
      case lang.ImplicationOp() => return smt.ImplicationOp
      case lang.NegationOp() => return smt.NegationOp
      // Comparison operators.
      case lang.EqualityOp() => return smt.EqualityOp
      case lang.InequalityOp() => return smt.InequalityOp
      case lang.DistinctOp() => return smt.InequalityOp
      // Record select.
      case lang.RecordSelect(r) => return smt.RecordSelectOp(r.name)
      // Quantifiers
      case lang.ForallOp(vs) => return smt.ForallOp(vs.map(v => smt.Symbol(v._1.toString, smt.Converter.typeToSMT(v._2))))
      case lang.ExistsOp(vs) => return smt.ExistsOp(vs.map(v => smt.Symbol(v._1.toString, smt.Converter.typeToSMT(v._2))))
      case lang.ITEOp() => return smt.ITEOp
      // Polymorphic operators are not allowed.
      case p : lang.PolymorphicOperator =>
        throw new Utils.RuntimeError("Polymorphic operators must have been eliminated by now.")
      case _ => throw new Utils.UnimplementedException("Operator not supported yet: " + op.toString)
    }
  }

  def smtToOp(op : smt.Operator, args : List[smt.Expr]) : lang.Operator = {
    op match {
      // Integer operators.
      case smt.IntLTOp => return lang.IntLTOp()
      case smt.IntLEOp => return lang.IntLEOp()
      case smt.IntGTOp => return lang.IntGTOp()
      case smt.IntGEOp => return lang.IntGEOp()
      case smt.IntAddOp => return lang.IntAddOp()
      case smt.IntSubOp => 
        if (args.size == 1) lang.IntUnaryMinusOp()
        else                lang.IntSubOp()
      case smt.IntMulOp => return lang.IntMulOp()
      // Bitvector operators.
      case smt.BVLTOp(w) => return lang.BVLTOp(w)
      case smt.BVLEOp(w) => return lang.BVLEOp(w)
      case smt.BVGTOp(w) => return lang.BVGTOp(w)
      case smt.BVGEOp(w) => return lang.BVGEOp(w)
      case smt.BVAddOp(w) => return lang.BVAddOp(w)
      case smt.BVSubOp(w) => return lang.BVSubOp(w)
      case smt.BVMulOp(w) => return lang.BVMulOp(w)
      case smt.BVMinusOp(w) => lang.BVUnaryMinusOp(w)
      case smt.BVAndOp(w) => return lang.BVAndOp(w)
      case smt.BVOrOp(w) => return lang.BVOrOp(w)
      case smt.BVXorOp(w) => return lang.BVXorOp(w)
      case smt.BVNotOp(w) => return lang.BVNotOp(w)
      case smt.BVExtractOp(hi, lo) => return lang.ConstExtractOp(lang.ConstBitVectorSlice(hi, lo))
      case smt.BVConcatOp(_) => return lang.ConcatOp()
      // Boolean operators.
      case smt.ConjunctionOp => return lang.ConjunctionOp()
      case smt.DisjunctionOp => return lang.DisjunctionOp()
      case smt.IffOp => return lang.IffOp()
      case smt.ImplicationOp => return lang.ImplicationOp()
      case smt.NegationOp => return lang.NegationOp()
      // Comparison operators.
      case smt.EqualityOp => return lang.EqualityOp()
      case smt.InequalityOp => return lang.InequalityOp()
      // Record select.
      case smt.RecordSelectOp(name) => return lang.RecordSelect(lang.Identifier(name))  
      // Quantifiers
      case smt.ForallOp(vs) => return lang.ForallOp(vs.map(v => (lang.Identifier(v.id), smt.Converter.smtToType(v.symbolTyp))))
      case smt.ExistsOp(vs) => return lang.ExistsOp(vs.map(v => (lang.Identifier(v.id), smt.Converter.smtToType(v.symbolTyp))))
      case smt.ITEOp => return lang.ITEOp()
      case _ => throw new Utils.UnimplementedException("Operator not supported yet: " + op.toString)
    }
  }

  def _exprToSMT(expr : lang.Expr, scope : lang.Scope, past : Int, idToSMT : ((lang.Identifier, lang.Scope, Int) => smt.Expr)) : smt.Expr = {
    def toSMT(expr : lang.Expr, scope : lang.Scope, past : Int) : smt.Expr = _exprToSMT(expr, scope, past, idToSMT)
    def toSMTs(es : List[lang.Expr], scope : lang.Scope, past : Int) : List[smt.Expr] = es.map((e : lang.Expr) => toSMT(e, scope, past))

     expr match {
       case id : lang.Identifier => idToSMT(id, scope, past)
       case lang.IntLit(n) => smt.IntLit(n)
       case lang.BoolLit(b) => smt.BooleanLit(b)
       case lang.BitVectorLit(bv, w) => smt.BitVectorLit(bv, w)
       case lang.ConstArrayLit(value, arrTyp) =>
         smt.ConstArrayLit(toSMT(value, scope, past).asInstanceOf[smt.Literal], typeToSMT(arrTyp).asInstanceOf[ArrayType])
       case lang.StringLit(_) => throw new Utils.RuntimeError("Strings are not supported in smt.Converter")
       case lang.Tuple(args) => smt.MakeTuple(toSMTs(args, scope, past))
       case opapp : lang.OperatorApplication =>
         val op = opapp.op
         val args = opapp.operands
         op match {
           case lang.OldOperator() | lang.PastOperator() =>
             toSMT(args(0), scope, 1)
           case lang.HistoryOperator() =>
             toSMT(args(0), scope, args(1).asInstanceOf[lang.IntLit].value.toInt)
           case lang.GetNextValueOp() =>
             toSMT(args(0), scope, past)
           case lang.ConcatOp() =>
             val scopeWOpApp = scope + opapp
             val argsInSMT = toSMTs(args, scopeWOpApp, past)
             Utils.assert(argsInSMT.length == 2, "Bitvector concat must have two arguments.")
             Utils.assert(argsInSMT.forall(_.typ.isBitVector), "Argument to bitvector concat must be a bitvector.")
             val width = argsInSMT.foldLeft(0)((acc, ai) => ai.typ.asInstanceOf[BitVectorType].width + acc)
             smt.OperatorApplication(smt.BVConcatOp(width), argsInSMT)
           case _ =>
             val scopeWOpApp = scope + opapp
             val argsInSMT = toSMTs(args, scopeWOpApp, past)
             smt.OperatorApplication(opToSMT(op), argsInSMT)
         }
       case lang.ArraySelectOperation(a,index) =>
         smt.ArraySelectOperation(toSMT(a, scope, past), toSMTs(index, scope, past))
       case lang.ArrayStoreOperation(a,index,value) =>
         smt.ArrayStoreOperation(toSMT(a, scope, past), toSMTs(index, scope, past), toSMT(value, scope, past))
       case lang.FuncApplication(f,args) => f match {
         case lang.Identifier(id) =>
           smt.FunctionApplication(toSMT(f, scope, past), toSMTs(args, scope, past))
         case lang.Lambda(idtypes,le) =>
           // FIXME: beta sub
           throw new Utils.UnimplementedException("Beta reduction is not implemented yet.")
         case _ =>
           throw new Utils.RuntimeError("Should never get here.")
       }
       // Unimplemented operators.
       case lang.Lambda(ids,le) =>
         throw new Utils.UnimplementedException("Lambdas are not yet implemented.")
       // Troublesome operators.
       case lang.FreshLit(t) =>
         throw new Utils.RuntimeError("Should never get here. FreshLits must have been rewritten by this point.")
       case lang.ExternalIdentifier(_, _) =>
         throw new Utils.RuntimeError("Should never get here. ExternalIdentifiers must have been rewritten by this point.")
    }
  }

  def exprToSMT(expr : lang.Expr, scope : lang.Scope) : smt.Expr = {
    def idToSMT(id : lang.Identifier, scope : lang.Scope, past : Int) : smt.Expr = {
      val typ = scope.typeOf(id).get
      smt.Symbol(id.name, typeToSMT(typ))
    }
    _exprToSMT(expr, scope, 0, idToSMT)
  }

  def exprToSMT(expr : lang.Expr, idToSMT : (lang.Identifier, lang.Scope, Int) => smt.Expr, scope : lang.Scope) : smt.Expr = {
    _exprToSMT(expr, scope, 0, idToSMT)
  }

  def smtToExpr(expr : smt.Expr) : lang.Expr = {
    def toExpr(expr : smt.Expr) : lang.Expr = smtToExpr(expr)
    def toExprs(es : List[smt.Expr]) : List[lang.Expr] = es.map((e : smt.Expr) => toExpr(e))

    expr match {
      case smt.Symbol(id, symbolTyp) => lang.Identifier(id)
      case smt.IntLit(n) => lang.IntLit(n)
      case smt.BooleanLit(b) => lang.BoolLit(b)
      case smt.BitVectorLit(bv, w) => lang.BitVectorLit(bv, w)
      case opapp : smt.OperatorApplication =>
        val op = opapp.op
        val args = opapp.operands
        lang.OperatorApplication(smtToOp(op, args), toExprs(args))
      case smt.ArraySelectOperation(a,index) =>
        lang.ArraySelectOperation(toExpr(a), toExprs(index))
      case smt.ArrayStoreOperation(a,index,value) =>
        lang.ArrayStoreOperation(toExpr(a), toExprs(index), toExpr(value))
      case smt.FunctionApplication(f, args) =>
        f match {
          case smt.Symbol(id, symbolTyp) =>
            UclidMain.println("Function application of f == " + f.toString)
            lang.FuncApplication(lang.Identifier(id), toExprs(args))
          case _ =>
            throw new Utils.RuntimeError("Should never get here.")
        }
      case _ =>
        throw new Utils.UnimplementedException("'" + expr + "' is not yet supported.")
    }
  }

  def renameSymbols(expr : smt.Expr, renamerFn : ((String, smt.Type) => String)) : smt.Expr = {
    def rename(e : smt.Expr) = renameSymbols(e, renamerFn)
    expr match {
      case Symbol(name,typ) =>
        return Symbol(renamerFn(name, typ), typ)
      case OperatorApplication(op,operands) =>
        return OperatorApplication(op, operands.map(rename(_)))
      case ArraySelectOperation(e, index) =>
        return ArraySelectOperation(rename(e), index.map(rename(_)))
      case ArrayStoreOperation(e, index, value) =>
        return ArrayStoreOperation(rename(e), index.map(rename(_)), rename(value))
      case FunctionApplication(e, args) =>
        return FunctionApplication(rename(e), args.map(rename(_)))
      case Lambda(syms,e) =>
        return Lambda(syms.map((sym) => Symbol(renamerFn(sym.id, sym.typ), sym.typ)), rename(e))
      case IntLit(_) | BitVectorLit(_,_) | BooleanLit(_) =>
        return expr
    }
  }
}
