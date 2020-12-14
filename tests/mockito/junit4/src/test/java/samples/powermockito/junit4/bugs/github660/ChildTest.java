package samples.powermockito.junit4.bugs.github660;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.io.PrintStream;

public class ChildTest extends ParentTest{

    protected PrintStream outMock;

    public ChildTest(){
        super();
        outMock = Mockito.mock(PrintStream.class);
        System.setErr(outMock);

    }
    @Test
    public void test_A(){
        Mockito.verify(outMock,Mockito.times(0)).println("Notifications are not supported when all test-instances are created first!");
    }

    @Test
    public void test_B(){
        Mockito.verify(outMock,Mockito.times(0)).println("Notifications are not supported when all test-instances are created first!");
    }
}
