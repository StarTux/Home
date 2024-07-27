package com.cavetale.home.sql;

import org.junit.Test;
import static com.winthier.sql.SQLDatabase.testTableCreation;

public final class SQLTest {
    public void test() {
        for (var it : SQLStatic.getAllTableClasses()) {
            System.out.println(testTableCreation(it));
        }
    }
}
