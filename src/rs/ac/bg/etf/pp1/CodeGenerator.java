package rs.ac.bg.etf.pp1;

import java.util.Stack;

import com.sun.javafx.binding.SelectBinding.AsInteger;

import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.ReturnStat;
import rs.ac.bg.etf.pp1.ast.ReturnExprStat;
import rs.ac.bg.etf.pp1.ast.Add;
import rs.ac.bg.etf.pp1.ast.AssAdd;
import rs.ac.bg.etf.pp1.ast.AssDiv;
import rs.ac.bg.etf.pp1.ast.AssMod;
import rs.ac.bg.etf.pp1.ast.AssMul;
import rs.ac.bg.etf.pp1.ast.AssSub;
import rs.ac.bg.etf.pp1.ast.BoolConst;
import rs.ac.bg.etf.pp1.ast.CharConst;
import rs.ac.bg.etf.pp1.ast.DesArr;
import rs.ac.bg.etf.pp1.ast.Designator;
import rs.ac.bg.etf.pp1.ast.DesignatorAssignop;
import rs.ac.bg.etf.pp1.ast.DesignatorAssignopRight;
import rs.ac.bg.etf.pp1.ast.DesignatorMethodCall;
import rs.ac.bg.etf.pp1.ast.DessDec;
import rs.ac.bg.etf.pp1.ast.DessInc;
import rs.ac.bg.etf.pp1.ast.Df;
import rs.ac.bg.etf.pp1.ast.Div;
import rs.ac.bg.etf.pp1.ast.ExprTerm;
import rs.ac.bg.etf.pp1.ast.ExprTermAddopLeft;
import rs.ac.bg.etf.pp1.ast.ExprTermAddopRight;
import rs.ac.bg.etf.pp1.ast.Expression;
import rs.ac.bg.etf.pp1.ast.Fact;
import rs.ac.bg.etf.pp1.ast.Factor;
import rs.ac.bg.etf.pp1.ast.FactorExprBegin;
import rs.ac.bg.etf.pp1.ast.FuncCall;
import rs.ac.bg.etf.pp1.ast.FuncCallNoPrs;
import rs.ac.bg.etf.pp1.ast.InitBool;
import rs.ac.bg.etf.pp1.ast.InitChar;
import rs.ac.bg.etf.pp1.ast.InitNum;
import rs.ac.bg.etf.pp1.ast.JustFactor;
import rs.ac.bg.etf.pp1.ast.MethodTypeName;
import rs.ac.bg.etf.pp1.ast.MethodVoidName;
import rs.ac.bg.etf.pp1.ast.Mod;
import rs.ac.bg.etf.pp1.ast.Mul;
import rs.ac.bg.etf.pp1.ast.MulopLeftFactor;
import rs.ac.bg.etf.pp1.ast.MulopRightFact;
import rs.ac.bg.etf.pp1.ast.NegExpr;
import rs.ac.bg.etf.pp1.ast.NewTypeExpr;
import rs.ac.bg.etf.pp1.ast.NoNumConst;
import rs.ac.bg.etf.pp1.ast.NumConst;
import rs.ac.bg.etf.pp1.ast.OptNumConst;
import rs.ac.bg.etf.pp1.ast.PExpr;
import rs.ac.bg.etf.pp1.ast.PrintStat;
import rs.ac.bg.etf.pp1.ast.ProgName;
import rs.ac.bg.etf.pp1.ast.ReadStat;
import rs.ac.bg.etf.pp1.ast.SimpleDes;
import rs.ac.bg.etf.pp1.ast.Sub;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.ast.Term;
import rs.ac.bg.etf.pp1.ast.TermList;
import rs.ac.bg.etf.pp1.ast.TypeMethodDecl;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.ast.VoidMethodDecl;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {

	private int mainPc;
	private Stack<Integer> operations;
	private boolean assignment;
	private int currentNestLevel;

	private boolean parens[][];

	private static final int ADD = 0;
	private static final int SUB = 1;
	private static final int MUL = 2;
	private static final int DIV = 3;
	private static final int MOD = 4;

	public CodeGenerator() {
		operations = new Stack<Integer>();
		currentNestLevel = 0;
		parens = new boolean[5][20];

		for (int i = 0; i < 5; i++)
			for (int j = 0; j < 20; j++)
				parens[i][j] = false;
	}

	public int getMainPc() {
		return mainPc;

	}

	/******************* PROG NAME **********************/
	public void visit(ProgName progName) {
		Obj charMethod = Tab.find("chr");
		charMethod.setAdr(Code.pc);
		Obj ordMethod = Tab.find("ord");
		ordMethod.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n + 0);
		Code.put(Code.exit);
		Code.put(Code.return_);
		Obj lenMethod = Tab.find("len");
		lenMethod.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n + 0);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	/********************* CONSTANTS ***********************/
	public void visit(InitNum initNum) {
		Obj con = Tab.insert(Obj.Con, "$", initNum.struct);
		con.setLevel(0);
		con.setAdr(initNum.getValue());
		Code.load(con);
	}

	public void visit(InitChar initC) {
		Obj con = Tab.insert(Obj.Con, "$", initC.struct);
		con.setLevel(0);
		con.setAdr(initC.getValue());
		Code.load(con);
	}

	public void visit(InitBool initBool) {
		Obj con = Tab.insert(Obj.Con, "$", initBool.struct);
		con.setLevel(0);
		con.setAdr(initBool.getValue());
		Code.load(con);
	}

	/*********************** METHOD **************************/

	public void visit(MethodTypeName mtn) {
		mtn.obj.setAdr(Code.pc);
		SyntaxNode methodNode = mtn.getParent();
		VarCounter varCnt = new VarCounter();
		methodNode.traverseTopDown(varCnt);

		FormParamCounter fpCnt = new FormParamCounter();
		methodNode.traverseTopDown(fpCnt);

		Code.put(Code.enter);
		Code.put(fpCnt.getCount());
		Code.put(fpCnt.getCount() + varCnt.getCount());
	}

	public void visit(TypeMethodDecl tmd) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(MethodVoidName mtn) {
		String name = mtn.getMethName();
		if (name.equalsIgnoreCase("main")) {
			mainPc = Code.pc;
		}
		mtn.obj.setAdr(Code.pc);
		SyntaxNode methodNode = mtn.getParent();
		VarCounter varCnt = new VarCounter();
		methodNode.traverseTopDown(varCnt);

		FormParamCounter fpCnt = new FormParamCounter();
		methodNode.traverseTopDown(fpCnt);

		Code.put(Code.enter);
		Code.put(fpCnt.getCount());
		Code.put(fpCnt.getCount() + varCnt.getCount());
	}

	public void visit(VoidMethodDecl vmd) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	/*********************** STATEMENTS ***********************/
	public void visit(PrintStat ps) {
		Struct type = ps.getExpression().struct;
		if (type.getKind() == Struct.Array) {
			while (type.getElemType() != null) {
				type = type.getElemType();
			}
		}

		if (type == Tab.charType) {
			if (ps.getOptionalNumConst() instanceof NoNumConst) {
				Code.loadConst(1);
			} else {
				OptNumConst onc = (OptNumConst) ps.getOptionalNumConst();
				Code.loadConst(onc.getN1());
			}
			Code.put(Code.bprint);
		} else {
			if (ps.getOptionalNumConst() instanceof NoNumConst) {
				Code.loadConst(5);
			} else {
				OptNumConst onc = (OptNumConst) ps.getOptionalNumConst();
				Code.loadConst(onc.getN1());
			}
			Code.put(Code.print);
		}
	}

	public void visit(ReadStat rs) {
		if(rs.getDesignator().obj.getType() == Tab.intType || rs.getDesignator().obj.getType() == SemanticAnalyzer.boolType
				|| rs.getDesignator().obj.getType().getKind() == 3){
			Code.put(Code.read);
			Code.store(rs.getDesignator().obj);
		}else{
			Code.put(Code.bread);
			Code.store(rs.getDesignator().obj);
		}
	}

	public void visit(ReturnExprStat returnExpr) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(ReturnStat returnNoExpr) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	/****************** DESIGNATORS ********************/
	public void visit(DesArr sd) {
		Class parentClass = sd.getParent().getClass();
		if ((parentClass != DesignatorAssignop.class)
				&& (parentClass != ReadStat.class) 
				&& (parentClass != DessInc.class)
				&& (parentClass != DessDec.class)
				&& parentClass != FuncCall.class
				&& parentClass != FuncCallNoPrs.class
				&& parentClass != DesignatorMethodCall.class
				) {

			if(parentClass == DesignatorAssignopRight.class || assignment) {
				Code.put(Code.dup2);
				assignment = false;
			}
			Code.load(sd.obj);
		}
		
		
		
	}

	public void visit(SimpleDes sd) {
		Class parentClass = sd.getParent().getClass();
		if ((parentClass != DesignatorAssignop.class) 
				&& parentClass != FuncCall.class
				&& parentClass != FuncCallNoPrs.class 
				&& parentClass != DesignatorMethodCall.class
				&& (parentClass != ReadStat.class) 
				&& sd.obj.getKind() != Obj.Type) {
			Code.load(sd.obj);
		}

		if (assignment && parentClass!=DesArr.class) {
			assignment = false;
		}
	}

	/************* DESIGNATOR STATEMENTS ******************/
	public void visit(DessInc desInc) {
		Obj obj = desInc.getDesignator().obj;

		if (obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
			if (obj.getType() == Tab.charType) {
				Code.put(Code.baload);
				Code.loadConst(1);
				Code.put(Code.add);
				Code.put(Code.bastore);
			} else {
				Code.put(Code.aload);
				Code.loadConst(1);
				Code.put(Code.add);
				Code.put(Code.astore);
			}

		} else {
			Code.load(obj);
			Code.loadConst(1);
			Code.put(Code.add);
			Code.store(obj);
		}
	}

	public void visit(DessDec desDec) {
		Obj obj = desDec.getDesignator().obj;

		if (obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
			if (obj.getType() == Tab.charType) {
				Code.put(Code.baload);
				Code.loadConst(1);
				Code.put(Code.sub);
				Code.put(Code.bastore);
			} else {
				Code.put(Code.aload);
				Code.loadConst(1);
				Code.put(Code.sub);
				Code.put(Code.astore);
			}

		} else {
			Code.load(obj);
			Code.loadConst(1);
			Code.put(Code.sub);
			Code.store(obj);
		}
	}

	public void visit(DesignatorAssignop da) {
		Obj o = da.getDesignator().obj;
		Code.store(o);
	}

	public void visit(DesignatorAssignopRight da) {
		int c = operations.pop();
		Code.put(c);

		Designator des = da.getDesignator();
		if (des instanceof SimpleDes) {
			Code.put(Code.dup);
			Code.store(des.obj);
		} else {
			Code.put(Code.dup_x2);
			Code.put(Code.astore);
		}
		Code.put(Code.pop);
		operations.clear();
		assignment = false;
	}

	public void visit(DesignatorMethodCall procCall) {
		Obj functionObj = procCall.getDesignator().obj;
		if (functionObj.getName().equals("chr") || functionObj.getName().equals("ord"))
			return;
		if (functionObj.getName().equals("len")) {
			Code.put(Code.arraylength);
			return;
		}
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
		if (procCall.getDesignator().obj.getType().getKind() != Struct.None) {
			Code.put(Code.pop);
		}
	}

	/******************* EXPRESSION *********************/
	public void visit(NegExpr neg) {
		Code.put(Code.neg);
	}

	
	public void visit(ExprTerm et) {
		Class parentClass = et.getParent().getClass();
		if(parentClass==ExprTermAddopLeft.class) {
			if(parens[ADD][currentNestLevel]) {
				Code.put(Code.add);
				parens[ADD][currentNestLevel]=false;
			}
			if(parens[SUB][currentNestLevel]) {
				Code.put(Code.sub);
				parens[SUB][currentNestLevel]=false;
			}
		
		}
	}

	public void visit(ExprTermAddopLeft expr) {
		parens[ADD][currentNestLevel] = 
				parens[SUB][currentNestLevel] = 
					false;
	}

	public void visit(ExprTermAddopRight expr) {
		if (Code.get(Code.pc - 1) == Code.aload && Code.get(Code.pc - 2) == Code.dup2) {
			Code.pc = Code.pc - 2;
			Code.put(Code.aload);
		}

		int c = operations.pop();
		Code.put(c);

		TermList t = expr.getTerm().getTermList();
		if (t instanceof JustFactor) {
			Factor f = ((JustFactor) t).getFactor();
			if (f instanceof Df) {
				if (((Df) f).getDesignator() instanceof DesArr) {
					Code.put(Code.dup_x2);
					Code.put(Code.astore);
				}
				if (((Df) f).getDesignator() instanceof SimpleDes) {
					Code.put(Code.dup);
					Code.store(((Df) f).getDesignator().obj);
				}
			}
		}
	}


	/********************** FACTOR **********************/
	public void visit(Df df) {
		String name = df.getDesignator().obj.getName();
		if (name.compareTo("eol") == 0) {
			Obj con = Tab.insert(Obj.Con, "$", df.struct);
			con.setLevel(0);
			con.setAdr('\n');
			Code.load(con);
		}
	}

	public void visit(FuncCallNoPrs fc) {
		Obj functionObj = fc.getDesignator().obj;
		if (functionObj.getName().equals("chr") || functionObj.getName().equals("ord"))
			return;
		if (functionObj.getName().equals("len")) {
			Code.put(Code.arraylength);
			return;
		}
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	public void visit(FuncCall funcCall) {
		Obj functionObj = funcCall.getDesignator().obj;
		if (functionObj.getName().equals("chr") || functionObj.getName().equals("ord"))
			return;
		if (functionObj.getName().equals("len")) {
			Code.put(Code.arraylength);
			return;
		}
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	public void visit(NewTypeExpr expr) {
		Code.put(Code.newarray);
		Struct str = expr.getType().struct;
		int code;
		if (str == Tab.charType)
			code = 0;
		else
			code = 1;
		Code.put(code);
	}
	
	public void visit(PExpr pexpr) {
		currentNestLevel--;
	}

	public void visit(NumConst cnst) {
		Obj con = Tab.insert(Obj.Con, "$", cnst.struct);
		con.setLevel(0);
		con.setAdr(cnst.getN1());
		Code.load(con);
	}

	public void visit(CharConst cnst) {
		Obj con = Tab.insert(Obj.Con, "$", cnst.struct);
		con.setLevel(0);
		con.setAdr(cnst.getC1());
		Code.load(con);
	}

	public void visit(BoolConst cnst) {
		Obj con = Tab.insert(Obj.Con, "$", cnst.struct);
		con.setLevel(0);
		con.setAdr(cnst.getB1());
		Code.load(con);
	}

	public void visit(FactorExprBegin feb) {
		currentNestLevel++;
	}

	/*********************** TERM **************************/
	public void visit(Term term) {
		SyntaxNode parent  = term.getParent();
		//Class parentClass = term.getParent().getClass();

		if (parent.getClass() == ExprTermAddopLeft.class) {
			if (parens[ADD][currentNestLevel]) {
				Code.put(Code.add);
				parens[ADD][currentNestLevel] = !parens[ADD][currentNestLevel];
			}

			if (parens[SUB][currentNestLevel]) {
				Code.put(Code.sub);
				parens[SUB][currentNestLevel] = !parens[SUB][currentNestLevel];
			}

		}
	}

	public void visit(JustFactor jf) {
		Class parentClass = jf.getParent().getClass();

		if (parentClass == MulopLeftFactor.class) {
			if (parens[MUL][currentNestLevel]) {
				Code.put(Code.mul);
				parens[MUL][currentNestLevel] = !parens[MUL][currentNestLevel];
			}
			if (parens[DIV][currentNestLevel]) {
				Code.put(Code.div);
				parens[DIV][currentNestLevel] = !parens[DIV][currentNestLevel];
			}
			if (parens[MOD][currentNestLevel]) {
				Code.put(Code.rem);
				parens[MOD][currentNestLevel] = !parens[MOD][currentNestLevel];
			}
		}
	}

	public void visit(MulopRightFact term) {
		if (Code.get(Code.pc - 1) == Code.aload && Code.get(Code.pc - 2) == Code.dup2) {
			Code.pc = Code.pc - 2;
			Code.put(Code.aload);
		}

		int c = operations.pop();
		Code.put(c);
		Factor f = term.getFact().getFactor();
		if (f instanceof Df) {
			if (((Df) f).getDesignator() instanceof DesArr) {
				Code.put(Code.dup_x2);
				Code.put(Code.astore);
			}
			if (((Df) f).getDesignator() instanceof SimpleDes) {
				Code.put(Code.dup);
				Code.store(((Df) f).getDesignator().obj);
			}
		}
	}

	public void visit(MulopLeftFactor term) {
		parens[MUL][currentNestLevel] 
				= parens[DIV][currentNestLevel] 
						= parens[MOD][currentNestLevel]
								= false;
	}

	public void visit(Fact fact) {
		Class parentClass = fact.getParent().getClass();

		if (parentClass == MulopLeftFactor.class) {
			if (parens[MUL][currentNestLevel]) {
				Code.put(Code.mul);
				parens[MUL][currentNestLevel] = false;
			}
			if (parens[DIV][currentNestLevel]) {
				Code.put(Code.div);
				parens[DIV][currentNestLevel] = false;
			}
			if (parens[MOD][currentNestLevel]) {
				Code.put(Code.rem);
				parens[MOD][currentNestLevel] = false;
			}
		}
	}

	/******************** ADDOP *********************/
	public void visit(Add add) {
		if (Code.get(Code.pc - 1) == Code.aload && Code.get(Code.pc - 2) == Code.dup2) {
			Code.pc = Code.pc - 2;
			Code.put(Code.aload);
		}
		parens[ADD][currentNestLevel] = true;
		//parens[SUB][currentNestLevel] = false;
	}

	public void visit(Sub sub) {
		if (Code.get(Code.pc - 1) == Code.aload && Code.get(Code.pc - 2) == Code.dup2) {
			Code.pc = Code.pc - 2;
			Code.put(Code.aload);
		}
		parens[SUB][currentNestLevel] = true;
		//parens[ADD][currentNestLevel] = false;
	}
	
	public void visit(AssAdd assAdd) {
		operations.push(Code.add);
		assignment = true;
	}
	
	public void visit(AssSub assSub) {
		operations.push(Code.sub);
		assignment = true;
	}

	/****************** MULOP ******************/
	public void visit(Mul mul) {
		if (Code.get(Code.pc - 1) == Code.aload 
				&& Code.get(Code.pc - 2) == Code.dup2) {
			Code.pc = Code.pc - 2;
			Code.put(Code.aload);
		}
		parens[MUL][currentNestLevel] = true;
	//	parens[DIV][currentNestLevel] = parens[MOD][currentNestLevel] = false;
	}

	public void visit(Div div) {
		if (Code.get(Code.pc - 1) == Code.aload && Code.get(Code.pc - 2) == Code.dup2) {
			Code.pc = Code.pc - 2;
			Code.put(Code.aload);
		}
		parens[DIV][currentNestLevel] = true;
	//	parens[MUL][currentNestLevel] = parens[MOD][currentNestLevel] = false;
	}

	public void visit(Mod mod) {
		if (Code.get(Code.pc - 1) == Code.aload && Code.get(Code.pc - 2) == Code.dup2) {
			Code.pc = Code.pc - 2;
			Code.put(Code.aload);
		}
		parens[MOD][currentNestLevel] = true;
	//	parens[DIV][currentNestLevel] = parens[MUL][currentNestLevel] = false;
	}

	
	public void visit(AssMul assMul) {
		operations.push(Code.mul);
		assignment = true;
	}
	public void visit(AssDiv assDiv) {
		operations.push(Code.div);
		assignment = true;
	}
	public void visit(AssMod assMod) {
		operations.push(Code.rem);
		assignment = true;
	}
}
