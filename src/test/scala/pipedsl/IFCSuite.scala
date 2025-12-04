package pipedsl

import org.scalatest.funsuite.AnyFunSuite
import pipedsl.common.Syntax.Prog
import pipedsl.passes._
import pipedsl.typechecker._
import pipedsl.typechecker.TypeInferenceWrapper.TypeInference

class IFCSuite extends AnyFunSuite {

  private def testIFC(code: String): Unit = {
    val p: Parser = new Parser(rflockImpl = "RenameRF")
    val prog = p.parseCode(code)

    // Run necessary passes before IFC check
    MarkNonRecursiveModulePass.run(prog)
    val inferredProg = new LockRegionInferencePass().run(prog)
    val verifProg = AddCheckpointHandlesPass.run(AddVerifyValuesPass.run(inferredProg))
    val canonProg2 = new CanonicalizePass().run(verifProg)
    val canonProg = new TypeInference(false).checkProgram(canonProg2)
    val basetypes = BaseTypeChecker.check(canonProg, None)

    IFCTypeChecker.check(canonProg, Some(basetypes))
  }

  test("IFC Fail Test") {
    val code = """
      pipe main()[] {
        secret<int<32>> s = 10<32>;
        // this fails because secret information is not allowed to flow to public.
        public<int<32>> p = s;
      }
      circuit {
        call main();
      }
    """
    assertThrows[RuntimeException] {
      testIFC(code)
    }
  }


  test("IFC Fail Test Implicit") {
    val code = """
      pipe main(num: public<int<32>>)[] {
        secret<int<32>> s = 10<32>;

        // this fails to type check.  Why?
        // because the if(secret) elevates the security context.
        // that means that the PC is given an elevated label.
        // in those contexts we cannot assign to a public variable.
        if (s > num) {
          public<int<32>> s2 = 11<32>;
        }
      }
      circuit {
        call main(3<32>);
      }
    """
    assertThrows[RuntimeException] {
      testIFC(code)
    }
  }

  test("IFC Pass Test") {
    val code = """
      pipe main()[]: bool {
        public<int<32>> p = 20<32>;
        secret<int<32>> s = p;

        if (p > 10<32>) {
            // assignment to Secret in Public context is OK
            secret<int<32>> s2 = 30<32>;
        }
      }
      circuit {
        call main();
      }
    """
    testIFC(code)
  }

  test("IFC Valid Implicit Flow") {
    val code = """
      pipe main(num: secret<int<32>>)[] {
        secret<int<32>> s = 10<32>;

        // this typechecks because secreet -> secret flows are ok.
        if (s > num) {
          secret<int<32>> s2 = 11<32>;
        }

        // now the security context is downgraded
        public<int<32>> s3 = 12<32>;
      }
      circuit {
        call main(3<32>);
      }
    """
    testIFC(code)
  }

}
