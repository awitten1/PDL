
Working on fork: https://github.com/awitten1/PDL

To compile, run make.

Runs parser:
```
./bin/pdl parse src/test/tests/risc-pipe/risc-pipe-3stg.pdl
```

Generate BSV:
```
mkdir out
./bin/pdl gen src/test/tests/risc-pipe/risc-pipe-3stg.pdl -o out
```

Running a single test
```
sbt "testOnly pipedsl.MainSuite -- -z \"Parser test\""
```

