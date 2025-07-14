package hub75

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.core.sim._
import scala.util.control.Breaks
import  MySpinalHardware._

case class PWM_Test() extends Component {
    val io = new Bundle
    {
        val start = in Bool()
        val Wait = in Bool()
        val mask = out Bits(10 bits)
        val blank = out Bool()
        val done = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(65536)
    val bitmask = Reg(Bits(10 bits)) init(0) 
    val done = Reg(Bool()) init(False)
    val waiting = Reg(Bool()) init(False)

/***-Wires-***/

/***-IO stuff-***/
    io.mask := bitmask
    io.done := done
    io.blank := False
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/
    val bitmask_next = (bitmask === 0) ? B"1000000000" | bitmask(0) ## bitmask(9 downto 1)

/***-Logic-***/
    when(io.start && done)
    {
        counter.setValue((bitmask).resize(16).asUInt)
        done := False
        bitmask := bitmask_next
    }elsewhen(bitmask === B"0000000000"){
        bitmask := bitmask_next
        done := True
    }elsewhen(!io.Wait && counter === 0 && bitmask =/= 0 && !done){
        counter.setValue((bitmask).resize(16).asUInt)
        bitmask := bitmask_next
    }elsewhen(counter === 0 && bitmask === B"1000000000"){
        done := True
    }elsewhen(counter > 0){
        io.blank := True
        counter.decrement()
    }

}


case class hub75_Test(val SX: Int) extends Component {
    val io = new Bundle
    {
        val start = in Bool()
        val Wait = in Bool()
        val Latch = out Bool()
        val Sclk = out Bool()
        val X = out UInt(8 bits)
        val done = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(256)
    val clk = Reg(Bool()) init(False)
    val running = Reg(Bool()) init(False)
    val done = Reg(Bool()) init(False)
/***-Wires-***/

/***-IO stuff-***/
    io.Latch := (counter === 0 && !io.Wait).rise()
    io.Sclk := clk
    io.done := done
    io.X := counter
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/

/***-Logic-***/
    when(counter === 0 && io.start && !running)
    {
        done := False
        running := True
        counter.setValue(SX)
    }elsewhen(counter =/= 0){
        when(clk){
            counter.decrement()
            clk := False
        }otherwise{
            clk := True
        }
    }elsewhen(!io.start && counter === 0){
        running := False
        done := True
    }otherwise{
        done := True
    }
}

case class hub75_top(val SX: Int, val SY: Int) extends Component {
    val io = new Bundle {
        val clear = out Bool()
        val brightness = in Bits(2 bits)
        val RamInterface = new Bundle
        {
            val Ready = out Bool()
            val Valid = in Bool()
            val Address = out Bits(16 bit)
            val DataIn  = in Bits(16 bit)
        }
        val hub75 = new Bundle {
            val RGB0 = new Bundle {
                val R = out Bool()
                val G = out Bool()
                val B = out Bool()
            }
            val RGB1 = new Bundle {
                val R = out Bool()
                val G = out Bool()
                val B = out Bool()
            }
            val Address = out Bits(5 bits)
            val Latch = out Bool()
            val Sclk = out Bool()
            val Blank = out Bool()
        }
    }

/***-Defines-***/    


/***-Registers-***/
    val hub_address = Counter(SX)

/***-Wires-***/
    val rgb565U = Bits(16 bits)
    val rgb565L = Bits(16 bits)

    val rotateFIFOs = False
    val rotateClk = False
    val LoadFIFO = False
    val FlushFIFO = False
    val rotateClk_D1 = RegNext(rotateClk)

/***-Streams-***/
    val UpperLine = StreamFifo(
      dataType = Bits(16 bits),
      depth    = SX+1
    )

    val LowerLine = StreamFifo(
      dataType = Bits(16 bits),
      depth    = SX+1
    )

    UpperLine.io.flush := False
    LowerLine.io.flush := False

    val UpperIn = Stream(Bits(16 bits))
    val LowerIn = Stream(Bits(16 bits))
    val UpperOut = Stream(Bits(16 bits))
    val LowerOut = Stream(Bits(16 bits))

    UpperLine.io.pop  >> UpperOut
    UpperLine.io.push << UpperIn
    LowerLine.io.pop  >> LowerOut
    LowerLine.io.push << LowerIn

/***-Blocks-***/
    val cscU = new RGB565toRGB888()
    val cscL = new RGB565toRGB888()

    cscU.io.brightness := io.brightness
    cscL.io.brightness := io.brightness

    cscU.io.rgb656  := rgb565U
    cscL.io.rgb656  := rgb565L

    val glow = new PWM_Test()
    val hub = new hub75_Test(SX)
    
    glow.io.start := False
    glow.io.Wait := False

    hub.io.start := False
    hub.io.Wait := False

    val demo = new FifoHandler(SX, SY)
    demo.io.clear := False 


    // val displayBuffer = new RamInit("./data/test.bin", 16, 16)
    // val demoAddrLast = RegNext(demo.io.RamInterface.Address)
    // val demoReadyLast = RegNext(demo.io.RamInterface.Ready)

/***-Routing-***/

    val X = SX - hub.io.X

    io.clear := False
    io.hub75.Sclk := hub.io.Sclk
    io.hub75.Latch := hub.io.Latch

    io.hub75.RGB0.R := (glow.io.mask & cscU.io.R8).asBits =/= 0
    io.hub75.RGB0.G := (glow.io.mask & cscU.io.G8).asBits =/= 0
    io.hub75.RGB0.B := (glow.io.mask & cscU.io.B8).asBits =/= 0

    io.hub75.RGB1.R := (glow.io.mask & cscL.io.R8).asBits =/= 0
    io.hub75.RGB1.G := (glow.io.mask & cscL.io.G8).asBits =/= 0
    io.hub75.RGB1.B := (glow.io.mask & cscL.io.B8).asBits =/= 0

    io.hub75.Blank := !glow.io.blank

    io.hub75.Address := hub_address.asBits.resize(5)

    io.RamInterface <> demo.io.RamInterface
    // displayBuffer.io.addra := demo.io.RamInterface.Address
    // demo.io.RamInterface.DataIn := displayBuffer.io.douta
    // demo.io.RamInterface.Valid := (demoAddrLast === demo.io.RamInterface.Address) && demoReadyLast
    // displayBuffer.io.dina := 0
    // displayBuffer.io.ena := True
    // displayBuffer.io.wea(0) := False

    
/***-LutChains-***/
        rgb565U := UpperOut.payload
        rgb565L := LowerOut.payload

/***-Logic-***/


    when(rotateFIFOs){
        UpperOut.ready := rotateClk
        UpperIn.valid := rotateClk
        UpperIn.payload := UpperOut.payload

        LowerOut.ready := rotateClk
        LowerIn.valid := rotateClk
        LowerIn.payload := LowerOut.payload

        demo.io.LowerOut.ready := False
        demo.io.UpperOut.ready := False
    }elsewhen(FlushFIFO){
        UpperOut.ready := rotateClk

        demo.io.UpperOut.ready := rotateClk
        UpperIn.valid := rotateClk
        UpperIn.payload := demo.io.UpperOut.payload

        LowerOut.ready := rotateClk

        demo.io.LowerOut.ready := rotateClk
        LowerIn.valid := rotateClk
        LowerIn.payload := demo.io.LowerOut.payload
    }elsewhen(LoadFIFO){
        UpperOut.ready := False

        demo.io.UpperOut.ready := True
        UpperIn.valid := demo.io.UpperOut.valid
        UpperIn.payload := demo.io.UpperOut.payload

        LowerOut.ready := False

        demo.io.LowerOut.ready := True
        LowerIn.valid := demo.io.LowerOut.valid
        LowerIn.payload := demo.io.LowerOut.payload
    }otherwise{
        UpperOut.ready := False

        UpperIn.valid := False
        UpperIn.payload := B"16'h0000"

        LowerOut.ready := False

        LowerIn.valid := False
        LowerIn.payload := B"16'h0000"

        demo.io.LowerOut.ready := False
        demo.io.UpperOut.ready := False
    }

    val InterfaceFMS = new StateMachine {
        /***-FMS-***/
        val Reset: State = new StateDelay(10) with EntryPoint {
            whenIsActive{
                hub_address.clear()
                UpperLine.io.flush := True
                LowerLine.io.flush := True
                demo.io.clear := True
                io.clear := True
            }
            whenCompleted {
                goto(WaitForLine)
            }
        }
        val WaitForLine: State = new State {
            whenIsActive{
                when(demo.io.lineReady){
                    goto(FillLineFIFO) 
                }
            }
        }
        val FillLineFIFO: State = new State {
            whenIsActive{
                when(UpperLine.io.occupancy === SX && LowerLine.io.occupancy === SX){
                    goto(SendData)
                }otherwise{
                    LoadFIFO := True
                }
            }
        }

        val SendData: State = new  State {
            whenIsActive{
                glow.io.Wait := True
                hub.io.start := True
                rotateFIFOs := True
                goto(SendDataWait)
            }
        }

        val SendDataWait: State = new  State {
            whenIsActive{
                glow.io.Wait := True

                when(glow.io.mask === B"0000000001"){
                    FlushFIFO := True
                }otherwise{
                    rotateFIFOs := True
                }
                
                rotateClk := hub.io.Sclk
                when(hub.io.done){
                    when(glow.io.mask === B"0000000001"){
                        hub_address.increment()
                    }
                    goto(RunPWM)
                }
            }
        }

        val RunPWM: State = new  State {
            whenIsActive{
                glow.io.start := True
                goto(SendData)
            }
        }
    }

}

case class RGB565toRGB888() extends Component
{
    val io = new Bundle {
        val brightness = in Bits(2 bits)
        val rgb656 = in Bits(16 bits)
        val R8 = out Bits(10 bits)
        val G8 = out Bits(10 bits)
        val B8 = out Bits(10 bits)
    }
    // val R8 = ( B"000" ## io.rgb656(15 downto 11))
    // val G8 = ( B"00" ## io.rgb656(10 downto 5))
    // val B8 = ( B"000" ## io.rgb656(4 downto 0))
    val R8 = (io.rgb656(15 downto 11)  ## io.rgb656(15 downto 13))
    val G8 = (io.rgb656(10 downto 5) ## io.rgb656(10 downto 9))
    val B8 = (io.rgb656(4 downto 0) ## io.rgb656(4 downto 2))
    val R16 = R8.resize(16).asUInt * R8.resize(16).asUInt
    val G16 = G8.resize(16).asUInt * G8.resize(16).asUInt
    val B16 = B8.resize(16).asUInt * B8.resize(16).asUInt
    io.R8 := R16(15 downto 6).asBits >> io.brightness.asUInt
    io.G8 := G16(15 downto 6).asBits >> io.brightness.asUInt
    io.B8 := B16(15 downto 6).asBits >> io.brightness.asUInt
}

case class RGB565toB16() extends Component
{
    val io = new Bundle {
        val R5 = in Bits(5 bits)
        val G6 = in Bits(6 bits)
        val B5 = in Bits(5 bits)
        val rgb656 = out Bits(16 bits)
    }
    val rgb656 = io.R5 ## io.G6 ## io.B5
    io.rgb656 := rgb656

}

case class BasicBall(val SX: BigInt, val SY: BigInt) extends Component
{
    val io = new Bundle {
        val update = in Bool()
        val reset = in Bool()
        val X = out UInt(8 bits)
        val Y = out UInt(8 bits)
    }

    val ballX = Reg(UInt(8 bits)) init(0)
    val ballY = Reg(UInt(8 bits)) init(10)
    val ballXDir = Reg(Bool) init(True)
    val ballYDir = Reg(Bool) init(True)

    io.X := ballX
    io.Y := ballY
    when(io.reset){
        ballX := 0
        ballY := 10
        ballXDir := True
        ballYDir := True
    }elsewhen(io.update.fall()){
        when(ballX < 1){
            ballXDir := True
        } elsewhen(ballX >= SX){
            ballXDir := False
        }

        when(ballY < 1) {
            ballYDir := True
        } elsewhen(ballY >= SY){
            ballYDir := False
        }
    }elsewhen(io.update.rise()){
        when(ballXDir) {
            ballX := ballX + 1
        } otherwise (ballX := ballX - 1)

        when(ballYDir) {
            ballY := ballY + 1
        } otherwise (ballY := ballY - 1)
    }
}

case class FifoHandler(val SX: Int, val SY: Int) extends Component
{
    val io = new Bundle {
        val clear = in Bool()
        val lineReady = out Bool()
        val UpperOut = master Stream (Bits(16 bit))
        val LowerOut = master Stream (Bits(16 bit))

        val RamInterface = new Bundle
        {
            val Ready = out Bool()
            val Valid = in Bool()
            val Address = out Bits(16 bit)
            val DataIn  = in Bits(16 bit)
        }
    }

/***-Defines-***/    

/***-Registers-***/
    val X = Counter(SX)
    val Y = Counter(SY/2)
    val Addr = CounterUpDownSet(16384) 

/***-Wires-***/
    val AddrSwitch = False

/***-Streams-***/
    val UpperLine = StreamFifo(
      dataType = Bits(16 bits),
      depth    = SX
    )

    val LowerLine = StreamFifo(
      dataType = Bits(16 bits),
      depth    = SX
    )

    val UpperIn = Stream(Bits(16 bits))
    val LowerIn = Stream(Bits(16 bits))

/***-Blocks-***/

/***-Routing-***/
    UpperLine.io.push << UpperIn
    LowerLine.io.push << LowerIn
    UpperLine.io.pop  >> io.UpperOut
    LowerLine.io.pop >> io.LowerOut

    UpperIn.payload := io.RamInterface.DataIn
    LowerIn.payload := io.RamInterface.DataIn

    UpperIn.valid := False
    LowerIn.valid := False

    UpperLine.io.flush := False
    LowerLine.io.flush := False

/***-LutChains-***/
    val addrLower = Addr.value + U"16'h1000"
    val Addrout =  AddrSwitch ? addrLower | Addr
/***-IO stuff-***/
    io.lineReady := UpperLine.io.occupancy === SX && LowerLine.io.occupancy === SX
    io.RamInterface.Address := Addrout.asBits
    io.RamInterface.Ready := False

/***-Logic-***/

    val InterfaceFMS = new StateMachine {
        /***-FMS-***/
        val Reset: State = new State with EntryPoint {
            whenIsActive{
                X.clear()
                Y.clear()
                Addr.setValue(0)
                UpperLine.io.flush := True
                LowerLine.io.flush := True
                when(!io.clear){
                    goto(Start)
                }
            }
        }

        val Start: State = new State
        {
            whenIsActive{
                when(io.clear){
                    goto(Reset)
                }otherwise{
                    when(UpperIn.ready && LowerIn.ready){
                        goto(GetUpperPixel)
                    }
                }
            }
        }

        val GetUpperPixel: State = new State
        {
            whenIsActive{
                when(io.clear){
                    goto(Reset)
                }otherwise{
                    io.RamInterface.Ready := True

                    when(io.RamInterface.Valid)
                    {
                        UpperIn.valid := True
                        goto(GetLowerPixel)
                    }
                }
            }
        }

        val GetLowerPixel: State = new State{
            whenIsActive{
                when(io.clear){
                    goto(Reset)
                }otherwise{
                    io.RamInterface.Ready := True
                    AddrSwitch := True
                    when(io.RamInterface.Valid)
                    {
                        LowerIn.valid := True
                        X.increment()
                        Addr.increment()
                        when(X.willOverflow){
                            Y.increment()
                            when(Y.willOverflow){
                                Addr.setValue(0)
                            }
                        }
                        when(UpperIn.ready && LowerIn.ready){
                            goto(GetUpperPixel)
                        }otherwise{
                            goto(Start)
                        }
                    }
                }
            }
        }
    }
}

object Hub75Sim extends App {
    Config.sim.compile(hub75_top(64,64)).doSim { dut =>
        //Fork a process to generate the reset and the clock on the dut
        dut.clockDomain.forkStimulus(period = 83)
        var c = 0;
        var cc = 0;
        val loop = new Breaks;
        loop.breakable {
            while (true) {
                dut.clockDomain.waitRisingEdge()

                c += 1
                if(c > 65536*2){
                    loop.break;
                }
            }
        }
    }
}
