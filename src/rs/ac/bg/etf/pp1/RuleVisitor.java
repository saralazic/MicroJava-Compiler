package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import org.apache.log4j.Logger;


public class RuleVisitor extends VisitorAdaptor{

	int printCallCount = 0;
	int varDeclCount = 0;
	
	Logger log = Logger.getLogger(getClass());

	public void visit(VarDeclarations vardecl){
		varDeclCount++;
		log.info("Prepoznata deklaracija");
	}
	
    public void visit(PrintStat print) {
		printCallCount++;
		log.info("Prepoznata naredba print");
	}

}
