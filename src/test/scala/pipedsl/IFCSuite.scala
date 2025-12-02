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

    // Run IFC check
    IFCTypeChecker.check(canonProg, Some(basetypes))
  }

  test("IFC Fail Test") {
    val code = """
      pipe main()[] {
        secret<int<32>> s = 10<32>;
        public<int<32>> p = s; // Secret to Public is FAIL
      }
      circuit {
        call main();
      }
    """
    assertThrows[RuntimeException] { // IFCTypeChecker throws RuntimeException subclasses
      testIFC(code)
    }
  }

  test("IFC Pass Test") {
    val code = """
      pipe main()[]: bool {
        public<int<32>> p = 20<32>;
        secret<int<32>> s = p; // Public to Secret is OK

        if (p > 10<32>) { // Public condition
            secret<int<32>> s2 = 30<32>; // Assignment to Secret in Public context is OK
        }
      }
      circuit {
        call main();
      }
    """
    testIFC(code)
  }
}
