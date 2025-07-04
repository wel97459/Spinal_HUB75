package hub75

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import scala.util.control.Breaks
import  MySpinalHardware._
case class PWM_Test() extends Component {
    val io = new Bundle
    {
        val value = in Bits(8 bits)
        val led = out Bool()
        val cycle = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(65536)
    val bitmask = Reg(Bits(9 bits)) init(0) 


/***-Wires-***/

/***-IO stuff-***/
io.led := (io.value & bitmask(7 downto 0)) =/= 0
io.cycle := False
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/
val bitmask_next = (bitmask === 0) ? B"100000000" | B"0" ## bitmask(8 downto 1)

/***-Logic-***/
    when(counter === 0)
    {
        when(bitmask_next === B"100000000"){
            counter.setValue(2048)
            io.cycle := True
        }otherwise{
            counter.setValue((bitmask << 4).resize(16).asUInt)
        }
        bitmask := bitmask_next
    }otherwise{
        counter.decrement()
    }

}

object Hub75Sim extends App {
    Config.sim.compile(PWM_Test()).doSim { dut =>
        //Fork a process to generate the reset and the clock on the dut
        dut.clockDomain.forkStimulus(period = 10)
        var c = 0;
        var cc = 0;
        val loop = new Breaks;

        loop.breakable {
            dut.io.value #= 10
            while (true) {
                dut.clockDomain.waitRisingEdge()

                c += 1
                if(c > 65536){
                    loop.break;
                }
            }
        }
    }
}