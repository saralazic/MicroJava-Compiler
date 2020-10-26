package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	boolean newArr = false;
	int printCallCount = 0;
	int varDeclCount = 0;
	int readCallCount = 0;
	Obj currentMethod = null;
	boolean returnFound = false;
	boolean errorDetected = false;
	int nVars;
	int currentLevel = 0;
	Struct currentType = Tab.noType;

	boolean mainFound;

	int nParams = 0;

	public static Struct boolType;

	int numConst;
	char charConst;
	boolean boolConst;

	boolean f_called;

	public SemanticAnalyzer() {
		Tab.init();
		boolType = new Struct(Struct.Int);
		Tab.insert(Obj.Type, "bool", boolType);
	}

	/*************************** ERRORS ***********************************/
	Logger log = Logger.getLogger(getClass());

	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.info(msg.toString());
	}

	public boolean passed() {
		return !errorDetected;
	}

	/************************ VISIT VARS **************************/
	@Override
	public void visit(VarItem varItem) {
		varDeclCount++;

		String name = varItem.getVarName();

		Obj varNode = Tab.find(name);
		int kind = Obj.Var;

		String glob = (currentLevel == 0) ? "globalna " : "";

		if (varNode == Tab.noObj) {
			// is the var declared as array
			if (varItem.getOptArray() instanceof Brackets) {
				Struct array = new Struct(Struct.Array, currentType);
				varNode = Tab.insert(kind, name, array);
				report_info("Deklarisan niz " + name, varItem);

			} else {// var is not an array
				varNode = Tab.insert(kind, name, currentType);
				report_info("Deklarisana " + glob + "promenljiva " + name, varItem);

			}
		} else {
			if (Tab.currentScope.findSymbol(name) != null) {
				report_error("Postoji vec deklarisana promenljiva " + name, varItem);
			} else {
				if (varItem.getOptArray() instanceof Brackets) {
					Struct array = new Struct(Struct.Array, currentType);
					varNode = Tab.insert(kind, name, array);
					report_info("Deklarisan niz " + name, varItem);

				} else {// var is not an array
					varNode = Tab.insert(kind, name, currentType);
					report_info("Deklarisana " + glob + "promenljiva " + name, varItem);
				}
			}

		}
	}

	/***************************** VISIT PROGRAM ********************************/
	public void visit(ProgName progName) {
		progName.obj = Tab.insert(Obj.Prog, progName.getProgName(), Tab.noType);
		Tab.openScope();
		currentLevel++;
		mainFound = false;
	}

	public void visit(Program program) {
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(program.getProgName().obj);
		Tab.closeScope();
		currentLevel--;

		if (!this.mainFound) {
			report_error("ERROR: Nije definisan main metod!", null);
		}
	}

	/******************************** VISIT CONST *****************************/
	public void visit(ConstItem constItem) {
		String name = constItem.getConstName();
		Struct str = constItem.getInitializer().struct;

		if (Tab.currentScope().findSymbol(name) != null) {
			report_error("Greska na liniji " + constItem.getLine() + ": Konstanta " + name + " je vec deklarisana!",
					null);
		} else {
			if (!currentType.compatibleWith(str)) {
				report_error("Greska na liniji " + constItem.getLine() + ": Greska u definiciji konstante  "
						+ constItem.getConstName() + "! Tipovi nisu kompatibilni!", null);
			} else {
				Obj cnst = Tab.insert(Obj.Con, name, str);
				constItem.struct = cnst.getType();
				switch (currentType.getKind()) {
				case Struct.Int:
					cnst.setAdr(numConst);
					break;
				case Struct.Char:
					cnst.setAdr(charConst);
					break;
				case Struct.Bool:
					cnst.setAdr(boolConst ? 1 : 0);
					break;
				default:
					report_error("Za konstante se mogu koristiti samo osnovni tipovi podataka!", null);
				}
				currentType = null;
			}
		}
	}

	public void visit(InitNum initNum) {
		initNum.struct = Tab.intType;
		numConst = initNum.getValue();
	}

	public void visit(InitChar initC) {
		initC.struct = Tab.charType;
		charConst = initC.getValue();
	}

	public void visit(InitBool initBool) {
		initBool.struct = boolType;
		boolConst = (initBool.getValue() == 0) ? false : true;
	}

	public void visit(ConstDeclarations cd) {
		currentType = null;
	}

	/********************************* VISIT TYPE *******************************/
	public void visit(Type type) {
		Obj typeNode = Tab.find(type.getTypeName());
		if (typeNode == Tab.noObj) {
			report_error("Nije pronadjen tip " + type.getTypeName() + " u tabeli simbola! ", null);
			type.struct = Tab.noType;
		} else {
			if (Obj.Type == typeNode.getKind()) {
				type.struct = typeNode.getType();
			} else {
				report_error("Greska: Ime " + type.getTypeName() + " ne predstavlja tip!", type);
				type.struct = Tab.noType;
			}
		}
		currentType = type.struct;
	}

	/***************************** FORM PAR ITEM *********************************/
	@Override
	public void visit(FormParItem formParItem) {
		String name = formParItem.getFormParName();
		Struct str = formParItem.getType().struct;
		if (Tab.currentScope().findSymbol(name) == null) {
			nParams++;
			if (formParItem.getOptArray() instanceof Brackets) {
				// array
				Struct type = new Struct(Struct.Array, str);
				Obj o = Tab.insert(Obj.Var, name, type);
				o.setFpPos(nParams);
			} else {
				Obj o = Tab.insert(Obj.Var, name, str);
				o.setFpPos(nParams);
			}
		} else {
			report_error("Greska na liniji " + formParItem.getLine() + ": Parametar " + name + " je vec deklarisan!",
					null);
		}
	}

	@Override
	public void visit(OptFp optFp) {
		f_called = true;
	}

	/***************************** VISIT METHODDECL *******************************/
	@Override
	public void visit(MethodTypeName methodTypeName) {
		String name = methodTypeName.getMethodName();

		if (name.equals("main")) {
			report_error("Main funkcija ne treba da ima povratni tip!", methodTypeName);
		}

		currentMethod = Tab.insert(Obj.Meth, name, methodTypeName.getType().struct);
		methodTypeName.obj = currentMethod;

		Tab.openScope();
		currentLevel++;
		report_info("Obradjuje se funkcija " + name, methodTypeName);
	}

	public void visit(TypeMethodDecl typeMethodDecl) {
		if (!returnFound && currentMethod.getType() != Tab.noType && !currentMethod.getName().equals("main")) {
			report_error("Semanticka greska na liniji " + typeMethodDecl.getLine() + ": funkcija "
					+ currentMethod.getName() + " nema return iskaz!", null);
		}
		currentMethod.setLevel(nParams);
		nParams = 0;

		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();

		returnFound = false;
		currentMethod = null;
		currentLevel--;
	}

	@Override
	public void visit(MethodVoidName methodVoidName) {
		String name = methodVoidName.getMethName();
		currentMethod = Tab.insert(Obj.Meth, name, Tab.noType);
		methodVoidName.obj = currentMethod;
		Tab.openScope();
		currentLevel++;
		if (name.equals("main"))
			mainFound = true;
		report_info("Obradjuje se funkcija " + name, methodVoidName);
	}

	@Override
	public void visit(VoidMethodDecl methodVoidDecl) {
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		currentMethod.setLevel(nParams);
		nParams = 0;
		currentMethod = null;
		currentLevel--;
		returnFound = false;
	}

	/*************************** EXPRESSIONS *****************************/
	public void visit(Expr expr) {
		expr.struct = expr.getExpressionList().struct;
	}

	public void visit(NegExpr negExpr) {
		negExpr.struct = negExpr.getExpressionList().struct;
		if (!negExpr.struct.compatibleWith(Tab.intType)) {
			report_error("Greska na liniji " + negExpr.getLine() + ": Izraz nije tipa int, ne moze minus!", null);
		}
	}
	
	public void visit(Expression expr) {
		expr.struct = expr.getExpressions().struct;
	}

	public void visit(ExprTerm exprTerm) {
		// report_info("Expr term", null);
		exprTerm.struct = exprTerm.getTerm().struct;
	}

	public void visit(ExprTermAddopLeft exprTermAddop) {
		// report_info("Addop", null);
		exprTermAddop.struct = Tab.noType;

		Struct expr = exprTermAddop.getExpressionList().struct;
		Struct term = exprTermAddop.getTerm().struct;

		if ((term.getKind() == Struct.Int)
				|| ((term.getKind() == Struct.Array) && (term.getElemType().getKind() == Struct.Int))) {
			if ((expr.getKind() == Struct.Int)
					|| ((expr.getKind() == Struct.Array) && (expr.getElemType().getKind() == Struct.Int))) {
				exprTermAddop.struct = expr;
				if (expr.getKind() == Struct.Array)
					exprTermAddop.struct = expr.getElemType();
				// report_info("Expr struct: "+expr, null);
			} else {
				report_error("Greska na liniji" + exprTermAddop.getLine() + ":Levi operand nije tipa int!", null);
			}
		} else {
			report_error("Greska na liniji" + exprTermAddop.getLine() + ":Desni operand nije tipa int!", null);
		}
	}

	public void visit(ExprTermAddopRight exprTermAddop) {
		// report_info("Addop", null);
		exprTermAddop.struct = Tab.noType;

		Struct expr = exprTermAddop.getExpressionList().struct;
		Struct term = exprTermAddop.getTerm().struct;

		if ((term.getKind() == Struct.Int)
				|| ((term.getKind() == Struct.Array) && (term.getElemType().getKind() == Struct.Int))) {
			if ((expr.getKind() == Struct.Int)
					|| ((expr.getKind() == Struct.Array) && (expr.getElemType().getKind() == Struct.Int))) {
				exprTermAddop.struct = expr;
				if (expr.getKind() == Struct.Array)
					exprTermAddop.struct = expr.getElemType();
				// report_info("Expr struct: "+expr, null);
			} else {
				report_error("Greska na liniji" + exprTermAddop.getLine() + ":Levi operand nije tipa int!", null);
			}
		} else {
			report_error("Greska na liniji" + exprTermAddop.getLine() + ":Desni operand nije tipa int!", null);
		}
	}

	/******************************** TERMS *************************************/
	public void visit(Term term) {
		term.struct = term.getTermList().struct;
	}
	
	public void visit(Fact fact) {
		fact.struct = fact.getFactor().struct;
	}

	public void visit(JustFactor justFactor) {
		// report_info("justFact", null);
		justFactor.struct = justFactor.getFactor().struct;
		// report_info("justFact "+justFactor.struct, null);
	}

	public void visit(MulopLeftFactor mlf) {
		// report_info("TermAndMulopFact", null);
		mlf.struct = Tab.noType;
		Struct fact = mlf.getFact().struct;
		Struct term = mlf.getTermList().struct;

		if ((fact == Tab.intType) || ((fact.getKind() == Struct.Array) && (fact.getElemType() == Tab.intType))) {
			if ((term == Tab.intType) || ((term.getKind() == Struct.Array) && (term.getElemType() == Tab.intType))) {
				mlf.struct = term;
			} else {
				report_error("Greska na liniji" + mlf.getLine() + ":Levi operand nije tipa int!", null);
			}
		} else {
			report_error("Greska na liniji" + mlf.getLine() + ":Desni operand nije tipa int!", null);
		}
	}

	public void visit(MulopRightFact mrf) {
		mrf.struct = Tab.noType;
		Struct fact = mrf.getFact().struct;
		Struct term = mrf.getTermList().struct;

		if ((fact == Tab.intType) || ((fact.getKind() == Struct.Array) && (fact.getElemType() == Tab.intType))) {
			if ((term == Tab.intType) || ((term.getKind() == Struct.Array) && (term.getElemType() == Tab.intType))) {
				mrf.struct = term;
			} else {
				report_error("Greska na liniji" + mrf.getLine() + ":Levi operand nije tipa int!", null);
			}
		} else {
			report_error("Greska na liniji" + mrf.getLine() + ":Desni operand nije tipa int!", null);
		}
	}

	/********************************** FACTORS ***********************************/
	// designator
	public void visit(Df df) {
		// report_info("DF", null);
		Struct type = df.getDesignator().obj.getType();
		if (type.getKind() == Struct.Array) {
			df.struct = type.getElemType();
		} else {
			df.struct = type;
		}
	}

	public void visit(NumConst numConst) {
		// report_info("numConst", null);
		numConst.struct = Tab.intType;
	}

	public void visit(CharConst charConst) {
		// report_info("CConst", null);
		charConst.struct = Tab.charType;
	}

	public void visit(BoolConst boolConst) {
		// report_info("BoolConst", null);
		boolConst.struct = Tab.find("bool").getType();
	}

	// new type
	public void visit(NewType newType) {
		// report_info("NewType", null);
		newType.struct = Tab.noObj.getType();
	}

	// new arr[n]
	public void visit(NewTypeExpr newTypeExpr) {
		// report_info("NewTypeExpr", null);
		Struct str = newTypeExpr.getExpression().struct;

		if ((str.getKind() != Struct.Int)
				&& !((str.getKind() == Struct.Array) && (str.getElemType().getKind() == Struct.Int))) {
			report_error("Greska na liniji " + newTypeExpr.getLine() + " : broj elemenata niza mora biti int!", null);
		}
		newArr = true;
		newTypeExpr.struct = new Struct(Struct.Array, newTypeExpr.getType().struct);
		newTypeExpr.struct.setElementType(newTypeExpr.getType().struct);
	}

	public void visit(PExpr pExpr) {
		// report_info("pExpr", null);
		Struct str = pExpr.getExpression().struct;
		pExpr.struct = str;
		if (str.getKind() == Struct.Array)
			pExpr.struct = str.getElemType();
	}

	public void visit(FuncCall funcCall) {
		// report_info("funcCall", null);
		Obj func = funcCall.getDesignator().obj;
		if (Obj.Meth == func.getKind()) {
			report_info("Pronadjen poziv funkcije " + func.getName() + " na liniji " + funcCall.getLine(), null);
			funcCall.struct = func.getType();
		} else {
			if (nParams != func.getLevel())
				report_error("Greska na liniji " + funcCall.getLine() + " : ime " + func.getName() + " nije funkcija!",
						null);
			funcCall.struct = Tab.noType;
			nParams = 0;
		}
	}

	public void visit(FuncCallNoPrs funcCall) {
		Obj func = funcCall.getDesignator().obj;
		if (Obj.Meth == func.getKind()) {
			if (func.getLevel() != 0)
				report_error("Greska na liniji " + funcCall.getLine() + " : Neodgovarajuci broj parametara", null);

			report_info("Pronadjen poziv funkcije " + func.getName() + " na liniji " + funcCall.getLine(), null);
			funcCall.struct = func.getType();
			nParams = 0;
		} else {
			funcCall.struct = Tab.noType;
		}
	}

	/*************************** DESIGNATOR *******************************/
	public void visit(SimpleDes simpleDes) {
		Obj o = Tab.find(simpleDes.getDesName());
		if (o == Tab.noObj) {
			report_error("Greska na liniji " + simpleDes.getLine() + " : ime " + simpleDes.getDesName()
					+ " nije deklarisano! ", null);
		}
		simpleDes.obj = o;
	}

	public void visit(DesArr desArr) {
		desArr.obj = Tab.find(desArr.getDesignator().obj.getName());
		if (desArr.obj == Tab.noObj) {
			report_error("Greska na liniji " + desArr.getLine() + " : ime " + desArr.getDesignator().obj.getName()
					+ " nije deklarisano! ", null);
		} else {
			if (desArr.getExpression().struct.getKind() != Struct.Int) {
				report_error("Greska na liniji " + desArr.getLine()
						+ " : izraz za odredjivanje elementa niza nije tipa int! ", null);
			} else {
				if (desArr.obj.getType().getKind() != Struct.Array) {
					report_error(
							"Greska na liniji " + desArr.getLine() + " : ime " + desArr.obj.getName() + " nije niz! ",
							null);
				}
			}
		}

		desArr.obj = new Obj(Obj.Elem, desArr.obj.getName(), desArr.obj.getType().getElemType());
	}

	/******************* STATEMENT *******************/
	public void visit(ReadStat readStat) {
		Obj arg = readStat.getDesignator().obj;
		int kind = arg.getKind();
		Struct struct = arg.getType();
		if (!struct.compatibleWith(Tab.intType) && !struct.compatibleWith(Tab.charType)
				&& !struct.compatibleWith(boolType)) {
			report_error(
					"Greska na liniji " + readStat.getLine() + ": Argument funkcije read nije tipa int, char, bool!",
					null);
		} else {
			if (kind != Obj.Var && arg.getType().getKind() != Struct.Array) {
				report_error("Greska na liniji " + readStat.getLine()
						+ ": Argument funkcije read nije promenljiva ni element niza!", null);
			} else {

				if (struct.getElemType() != null) {
					struct = struct.getElemType();
				}
				readCallCount++;
			}
		}
	}

	@Override
	public void visit(PrintStat printStat) {
		Struct arg = printStat.getExpression().struct;
		if (arg != Tab.intType && arg != Tab.charType && arg != boolType) {
			report_error("Semanticka greska na liniji " + printStat.getLine()
					+ ": Operand instrukcije PRINT mora biti char ili int tipa", null);
		} else {
			if (arg.getElemType() != null) {
				arg = arg.getElemType();
			}

			printCallCount++;
		}
	}
	
	
	public void visit(ReturnExprStat returnExpr) { // da li i za return bez expr???
		returnFound = true;
		Struct currMethType = currentMethod.getType();
		if (!currMethType.compatibleWith(returnExpr.getExpression().struct)) {
			report_error("Greska na liniji " + returnExpr.getLine() + " : " + "tip izraza u return naredbi ne slaze se sa tipom povratne vrednosti funkcije " + currentMethod.getName(), null);
		}
		if (currentMethod == null) {
			report_error("Greska na liniji " + returnExpr.getLine() + " : " + "Return se moze koristiti samo u okviru metode", null);
		}
	}

	public void visit(ReturnStat returnExpr) {
		if (currentMethod == null) {
			report_error("Greska na liniji " + returnExpr.getLine() + " : " + "Return se moze koristiti samo u okviru metode", null);
		}
	}

	/******************************* ACT PARS *****************/
	public void visit(ActParsMultiple apm) {
		nParams++;
	}

	public void visit(ActParsSimple aps) {
		nParams++;
	}

	/**************************** DESIGNATOR STATEMENT ****************************/

	public void visit(DessInc desInc) {
		Obj o = desInc.getDesignator().obj;
		if ((o.getType().getKind() != Struct.Int)
				&& !((o.getType().getKind() == Struct.Array) && (o.getType().getElemType().getKind() == Struct.Int))) {
			report_error("Greska na liniji " + desInc.getLine() + " : designator " + o.getName() + " nije tipa int!",
					null);
		} else
			report_info(o.getName() + "++", null);
	}

	public void visit(DessDec desDec) {
		Obj o = desDec.getDesignator().obj;
		if ((o.getType().getKind() != Struct.Int)
				&& !((o.getType().getKind() == Struct.Array) && (o.getType().getElemType().getKind() == Struct.Int))) {
			report_error("Greska na liniji " + desDec.getLine() + " : designator " + o.getName() + " nije tipa int!",
					null);
		} else
			report_info(o.getName() + "++", null);
	}

	public void visit(DesignatorAssignop desAssign) {
		Designator des = desAssign.getDesignator();
		Obj o = des.obj;
		Struct s = desAssign.getExpression().struct;

		if (o.getKind() != Obj.Var && o.getKind() != Obj.Elem) {
			report_error("Greska na liniji " + desAssign.getLine()
					+ " : Designator mora oznacavati promenljivu ili element niza", null);
		}

		Struct d = (o.getKind() == Struct.Array) ? o.getType().getElemType() : o.getType();

		if (!assignable(s, o.getType()) && !(d != currentType && newArr)) {
			report_error(
					"Greska na liniji " + desAssign.getLine() + " : nije moguca dodela jer tipovi nisu kompatibilni!",
					null);
		}
		newArr = false;
	}

	public void visit(DesignatorAssignopRight desAssign) {
		Designator des = desAssign.getDesignator();
		Obj o = des.obj;
		Struct s = desAssign.getExpression().struct;

		if (o.getKind() != Obj.Var && o.getKind() != Obj.Elem) {
			report_error("Greska na liniji " + desAssign.getLine()
					+ " : Designator mora oznacavati promenljivu ili element niza", null);
		}

		Struct d = (o.getKind() == Struct.Array) ? o.getType().getElemType() : o.getType();

		if (!assignable(s, o.getType()) && !(d != currentType && newArr)) {
			report_error(
					"Greska na liniji " + desAssign.getLine() + " : nije moguca dodela jer tipovi nisu kompatibilni!",
					null);
		}
		newArr = false;

	}

	public void visit(DesignatorMethodCall designatorMethodCall) {
		Obj o = designatorMethodCall.getDesignator().obj;
		if (o.getKind() != Obj.Meth)
			report_error("Greska na liniji " + designatorMethodCall.getLine()
					+ " : Designator mora oznacavati globalnu funkciju", null);
		if (o.getLevel() != nParams)
			report_error("Greska na liniji " + designatorMethodCall.getLine() + " : Neodgovarajuci broj parametara",
					null);

		report_info("Poziv funkcije ", designatorMethodCall);
		nParams = 0;

	}
	/************** AUXILIARY FUNCTION ************/

	public boolean assignable(Struct dst, Struct src) {
		// report_info(dst+" = "+src, null);

		boolean dstIsArray = dst.getKind() == Struct.Array;
		boolean srcIsArray = src.getKind() == Struct.Array;
		boolean dstsrc = dstIsArray && srcIsArray;

		if (dstsrc) {
			if (src.getElemType() != null && !src.getElemType().assignableTo(dst.getElemType()))
				return false;
			return true;
		}

		// samo src je array
		if (srcIsArray) {
			if (src.getElemType() != null && !src.getElemType().assignableTo(dst))
				return false;
			return true;
		}

		// samo dst je array
		if (dstIsArray) {
			if (!src.assignableTo(dst.getElemType()))
				return false;
			return true;
		}

		// niko nije array
		if (!src.assignableTo(dst))
			return false;

		return true;
	}

}
