package com.cisco.jtapi.agentgreeting;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;

import javax.telephony.*;
import javax.telephony.callcontrol.CallControlCall;
import javax.telephony.callcontrol.CallControlTerminalConnection;
import javax.telephony.callcontrol.events.CallCtlTermConnRingingEv;

import com.cisco.jtapi.extensions.*;

public class CtiPortThread extends Thread {

    private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

    private static void log(String msg) {
        System.out.println(dtf.format(LocalDateTime.now()) + " " + msg);
    }

    public static CallCtlTermConnRingingEv ctipDialinCallEvent;
    public static CiscoRTPOutputStartedEv ctipRTPOutputStartedEvent;

    @Override
    public void run() {

        try {
            // Initialize the gstreamer-java framework Gst object
            Utils.configurePaths();
            Gst.init();

            // Determine this PC's IP address and get an ephemeral port number
            // for registering RTP media for the CTI Port
            DatagramSocket sock1 = new DatagramSocket();
            InetAddress ctipRtpAddress = InetAddress.getLocalHost();
            int ctipRtpPort = sock1.getLocalPort();
            sock1.close();

            CiscoProvider provider = agentGreeting.provider;
            // Open the CTI_PORT_DN Address
            log("(CtiPortThread) Opening ctipAddress DN: " + agentGreeting.dotenv.get("CTI_PORT_DN"));
            CiscoAddress ctipAddress = (CiscoAddress) provider
                    .getAddress(agentGreeting.dotenv.get("CTI_PORT_DN"));
            CtiPortHandler ctipHandler = new CtiPortHandler();
            ctipAddress.addObserver(ctipHandler);
            // Open and register the CTI Port media terminal
            CiscoMediaTerminal ctipTerminal = (CiscoMediaTerminal) ctipAddress.getTerminals()[0];
            ctipTerminal.addObserver(ctipHandler);
            ctipTerminal.register(
                    ctipRtpAddress,
                    ctipRtpPort,
                    new CiscoMediaCapability[] { CiscoMediaCapability.G711_64K_30_MILLISECONDS });

            // Handle out-of-order arrival of address and terminal in-service events)
            log("(CtiPortThread) Awaiting CiscoTermInServiceEv for: " + ctipTerminal.getName() + "...");
            if (ctipTerminal.getState() == CiscoTerminal.OUT_OF_SERVICE)
                ctipHandler.ctipTerminalInService.waitTrue();
            log("(CtiPortThread) Awaiting CiscoAddrInServiceEv for: " + ctipAddress.getName() + "...");
            if (ctipAddress.getState() == CiscoAddress.OUT_OF_SERVICE)
                ctipHandler.ctipAddressInService.waitTrue();

            // Enable auto accept for incoming offering calls
            ctipAddress.setAutoAcceptStatus(CiscoAddress.AUTOACCEPT_ON, ctipTerminal);

            ctipAddress.addCallObserver(ctipHandler);

            log("(CtiPortThread) Awaiting dialin CallCtlTermConnRingingEv for: " + ctipTerminal.getName() + "...");
            ctipHandler.ctipCallRinging.waitTrue();

            // Via the newly populated ctipDialinCallEvent (see handler), drill/cast down to
            // a CallControlTerminalConnection so we can do some operations
            CallControlTerminalConnection ctipIncomingCctConnection = (CallControlTerminalConnection) ctipDialinCallEvent
                    .getTerminalConnection();

            // Answer the dialin call on the CTI Port
            log("(CtiPortThread) Answering dialin call from DN: "
                    + ctipDialinCallEvent.getCallingAddress().getName());
            ctipIncomingCctConnection.answer();

            // Wait for the RTP output started event to indicate we can begin streaming
            // audio
            log("(CtiPortThread) Awaiting CiscoRTPOutputStartedEv for: "
                    + ctipIncomingCctConnection.getTerminal().getName());
            ctipHandler.ctipRTPOutputStarted.waitTrue();

            // Create the GStreamer pipeline string to send audio from a file to the
            // caller's phone; 30ms RTP packet size (in nanoseconds!)
            String pipelineDescription = String.format(
                    "filesrc location=media/AnnMonitoring.wav ! wavparse ! mulawenc ! rtppcmupay max-ptime=30000000 ! udpsink host=%s port=%s",
                    ctipRTPOutputStartedEvent.getRTPOutputProperties().getRemoteAddress().getHostAddress(), 
                    ctipRTPOutputStartedEvent.getRTPOutputProperties().getRemotePort());
            // Instantiate the GStreamer pipline
            Pipeline pipeline = (Pipeline) Gst.parseLaunch(pipelineDescription);

            // Add a GStreamer message bus event listener, triggered when the file is
            // finished playing
            Bus bus = pipeline.getBus();
            bus.connect(new Bus.EOS() {
                public void endOfStream(GstObject source) {
                    Gst.quit();
                }
            });

            // Start the RTP stream
            log("(CtiPortThread) Playing greeting audio");
            pipeline.play();

            // Keep this thread alive until Gst.quit() is called in the EOS event handler
            Gst.main();

            log("(CtiPortThread) Dropping IVR call: " + ctipDialinCallEvent.getCall().toString());
            ((CallControlCall) ctipDialinCallEvent.getCall()).drop();

        } catch (
                InvalidArgumentException | ResourceUnavailableException | MethodNotSupportedException
                | PlatformException | CiscoRegistrationException | InvalidStateException
                | PrivilegeViolationException | SocketException | UnknownHostException e) {
            log("(CtiPortThread) Error in thread");
            e.printStackTrace();
        }

    }

}
