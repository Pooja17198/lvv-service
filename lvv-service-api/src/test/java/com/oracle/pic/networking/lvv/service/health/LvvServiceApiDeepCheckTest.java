package com.oracle.pic.networking.lvv.service.health;

import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LvvServiceApiDeepCheckTest {

    @Test
    void execute() {
        LvvServiceApiDeepCheck check = new LvvServiceApiDeepCheck();
        PrintWriter output = Mockito.mock(PrintWriter.class);
        check.execute(null, output);
        Mockito.verify(output, Mockito.times(1)).println(Mockito.anyString());
    }
}
