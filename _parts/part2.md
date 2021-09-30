---
title: Part 2 - World's Simplest SQL Compiler and Virtual Machine
date: 2017-08-31
---

We're making a clone of sqlite. The "front-end" of sqlite is a SQL compiler that parses a string and outputs an internal representation called bytecode.

This bytecode is passed to the virtual machine, which executes it.

{% include image.html url="assets/images/arch2.gif" description="SQLite Architecture (https://www.sqlite.org/arch.html)" %}

Breaking things into two steps like this has a couple advantages:
- Reduces the complexity of each part (e.g. virtual machine does not worry about syntax errors)
- Allows compiling common queries once and caching the bytecode for improved performance

With this in mind, let's refactor our `run` function and support two new keywords in the process:

```diff
    public void run() {
        while (true) {
            printPrompt();
            String input = readInput();
            Objects.requireNonNull(input);

            if (input.isEmpty()) {
                continue; // do nothing and loop
            }
-            if (input.equals(".exit")) {
-                System.out.println("Exiting - Good bye.");
-                System.exit(0);
-            } else {
-                System.out.printf("Unrecognized command: '%s'%n", input);
+            if (input.charAt(0) == '.') {
+                MetaCommandResult metaCommandResult = doMetaCommand(input);
+                switch (metaCommandResult) {
+                    case META_COMMAND_SUCCESS:
+                        continue;
+                    case META_COMMAND_UNRECOGNIZED_COMMAND:
+                        System.out.printf("Unrecognized command: '%s'%n", input);
+                        continue;
+                }
            }
+            PreparedStatement preparedStatement = prepareStatement(input);
+            switch (preparedStatement.statementCheckResult) {
+                case PREPARE_SUCCESS:
+                    break;
+                case PREPARE_UNRECOGNIZED_STATEMENT:
+                    System.out.printf("Unrecognized  keyword at start of: '%s'%n", input);
+                    continue;
+            }
+            executeStatement(preparedStatement.getStatement());
+            System.out.printf("Executed.%n");
        }
    }
```

Non-SQL statements like `.exit` are called "meta-commands". They all start with a dot, so we check for them and handle them in a separate function.

Next, we add a step that converts the line of input into our internal representation of a statement. This is our hacky version of the sqlite front-end.

Lastly, we pass the prepared statement to `executeStatement`. This function will eventually become our virtual machine.

Notice that two of our new functions return enums indicating success or failure:

```java
private enum MetaCommandResult {
    META_COMMAND_SUCCESS,
    META_COMMAND_UNRECOGNIZED_COMMAND
}

private enum PrepareResult {
    PREPARE_SUCCESS,
    PREPARE_UNRECOGNIZED_STATEMENT
}
```

"Unrecognized statement"? That seems a bit like an exception. But [exceptions should be exceptional](https://flylib.com/books/en/2.823.1.73/1/), so I'm using enum result codes wherever practical. My Java IDE will complain if my switch statement doesn't handle a member of the enum, so we can feel a little more confident we handle every result of a function. Expect more result codes to be added in the future.

`doMetaCommand` is just a wrapper for existing functionality that leaves room for more commands:

```java
private MetaCommandResult doMetaCommand(String input) {
    if (input.equals(".exit")) {
    System.out.println("Exiting - Good bye.");
    System.exit(0);}
    return MetaCommandResult.META_COMMAND_UNRECOGNIZED_COMMAND;
    }
```

Our "prepared statement" right now just contains an enum with two possible values. 
It will contain more data as we allow parameters in statements.  
Notice we directly pass the corresponding SQL text in the statement types, and make it available with `toString()`. This will make string matching easy.

```java
private enum StatementType {
    STATEMENT_INSERT("insert"),
    STATEMENT_SELECT("select")
    ;

    private final String text;

    StatementType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}

private class Statement {
    private final StatementType statementType;

    Statement(StatementType statementType) {
        this.statementType = statementType;
    }

    public StatementType getStatementType() {
        return statementType;
    }
}
```

`prepare_statement` (our "SQL Compiler") transform the input into a `PreparedStatement` containing the `PrepareResult` status and the "prepared statement".   
`prepare_statement` does not understand SQL right now. In fact, it only understands two words:
```java
    private class PreparedStatement {
    private PrepareResult prepareResult;
    private Statement statement;

    public PreparedStatement(PrepareResult statementCheckResult, Statement statement) {
        this.prepareResult = statementCheckResult;
        this.statement = statement;
    }

    public PrepareResult getPrepareResult() {
        return prepareResult;
    }

    public Statement getStatement() {
        return statement;
    }
}

private PreparedStatement prepareStatement(String input) {
    String preparedInput = input.toLowerCase();
    if (preparedInput.startsWith(StatementType.STATEMENT_INSERT.toString())) {
        Statement statement = new Statement(StatementType.STATEMENT_INSERT);
        return new PreparedStatement(PrepareResult.PREPARE_SUCCESS, statement);
    } else if (preparedInput.startsWith(StatementType.STATEMENT_SELECT.toString())) {
        Statement statement = new Statement(StatementType.STATEMENT_SELECT);
        return new PreparedStatement(PrepareResult.PREPARE_SUCCESS, statement);
    }

    return new PreparedStatement(PrepareResult.PREPARE_UNRECOGNIZED_STATEMENT, null);
}
```

Note that we use `startsWith` for the SQL statements (`insert`, `select`) since keywords are followed by some other text. (e.g. `insert 1 cstack foo@bar.com`)

Lastly, `executeStatement` contains a few stubs:
```java
private void executeStatement(Statement statement) {
    switch (statement.getStatementType()) {
        case STATEMENT_INSERT:
            System.out.println("This is where we would do an insert.");
            break;
        case STATEMENT_SELECT:
            System.out.println("This is where we would do a select.");
            break;
    }
}
```

Note that it doesn't return any error because there's nothing that could go wrong yet.

With these refactors, we now recognize two new keywords!
```shell
~ javac Main.java
homemadeDB > insert foo bar
This is where we would do an insert.
Executed.
homemadeDB > delete foo
Unrecognized  keyword at start of: 'delete foo'
homemadeDB > select
This is where we would do a select.
Executed.
homemadeDB > .tables
Unrecognized command: '.tables'
homemadeDB > .exit
Exiting - Good bye.
~
```

The skeleton of our database is taking shape... wouldn't it be nice if it stored data? In the next part, we'll implement `insert` and `select`, creating the world's worst data store. In the meantime, here's the entire diff from this part:

```diff
@@ -7,6 +7,63 @@ import java.util.Objects;
 
 public class Main {
 
+    private enum MetaCommandResult {
+        META_COMMAND_SUCCESS,
+        META_COMMAND_UNRECOGNIZED_COMMAND
+    }
+
+    private enum PrepareResult {
+        PREPARE_SUCCESS,
+        PREPARE_UNRECOGNIZED_STATEMENT
+    }
+
+    private enum StatementType {
+        STATEMENT_INSERT("insert"),
+        STATEMENT_SELECT("select")
+        ;
+
+        private final String text;
+
+        StatementType(final String text) {
+            this.text = text;
+        }
+
+        @Override
+        public String toString() {
+            return text;
+        }
+    }
+
+    private class Statement {
+        private final StatementType statementType;
+
+        Statement(StatementType statementType) {
+            this.statementType = statementType;
+        }
+
+        public StatementType getStatementType() {
+            return statementType;
+        }
+    }
+
+    private class PreparedStatement {
+        private PrepareResult prepareResult;
+        private Statement statement;
+
+        public PreparedStatement(PrepareResult statementCheckResult, Statement statement) {
+            this.prepareResult = statementCheckResult;
+            this.statement = statement;
+        }
+
+        public PrepareResult getPrepareResult() {
+            return prepareResult;
+        }
+
+        public Statement getStatement() {
+            return statement;
+        }
+    }
+
     public static void main(String[] args) {
         new Main().run();
     }
@@ -20,12 +77,57 @@ public class Main {
             if (input.isEmpty()) {
                 continue; // do nothing and loop
             }
-            if (input.equals(".exit")) {
-                System.out.println("Exiting - Good bye.");
-                System.exit(0);
-            } else {
-                System.out.printf("Unrecognized command: '%s'%n", input);
+            if (input.charAt(0) == '.') {
+                MetaCommandResult metaCommandResult = doMetaCommand(input);
+                switch (metaCommandResult) {
+                    case META_COMMAND_SUCCESS:
+                        continue;
+                    case META_COMMAND_UNRECOGNIZED_COMMAND:
+                        System.out.printf("Unrecognized command: '%s'%n", input);
+                        continue;
+                }
             }
+            PreparedStatement preparedStatement = prepareStatement(input);
+            switch (preparedStatement.prepareResult) {
+                case PREPARE_SUCCESS:
+                    break;
+                case PREPARE_UNRECOGNIZED_STATEMENT:
+                    System.out.printf("Unrecognized  keyword at start of: '%s'%n", input);
+                    continue;
+            }
+            executeStatement(preparedStatement.getStatement());
+            System.out.printf("Executed.%n");
+        }
+    }
+
+    private MetaCommandResult doMetaCommand(String input) {
+        if (input.equals(".exit")) {
+            System.out.println("Exiting - Good bye.");
+            System.exit(0);}
+        return MetaCommandResult.META_COMMAND_UNRECOGNIZED_COMMAND;
+    }
+
+    private PreparedStatement prepareStatement(String input) {
+        String preparedInput = input.toLowerCase();
+        if (preparedInput.startsWith(StatementType.STATEMENT_INSERT.toString())) {
+            Statement statement = new Statement(StatementType.STATEMENT_INSERT);
+            return new PreparedStatement(PrepareResult.PREPARE_SUCCESS, statement);
+        } else if (preparedInput.startsWith(StatementType.STATEMENT_SELECT.toString())) {
+            Statement statement = new Statement(StatementType.STATEMENT_SELECT);
+            return new PreparedStatement(PrepareResult.PREPARE_SUCCESS, statement);
+        }
+
+        return new PreparedStatement(PrepareResult.PREPARE_UNRECOGNIZED_STATEMENT, null);
+    }
+
+    private void executeStatement(Statement statement) {
+        switch (statement.getStatementType()) {
+            case STATEMENT_INSERT:
+                System.out.println("This is where we would do an insert.");
+                break;
+            case STATEMENT_SELECT:
+                System.out.println("This is where we would do a select.");
+                break;
+         }
+    }
...
```
