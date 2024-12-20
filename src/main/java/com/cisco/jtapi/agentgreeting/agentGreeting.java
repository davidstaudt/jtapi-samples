package com.cisco.jtapi.agentgreeting;

// Implements an agent greeting feature scenario.

// Devices used / requirements (configure these in .env):
//   * ALICE_DN / any phone with an enabled built-in bridge
//   * CTI_PORT_DN / CTI Port
//   * Any other phone to manually call ALICE_DN

// Scenario:
// 1. A call is placed to ALICE_DN from any phone
// 2. ALICE_DN answers the call
// 3. An agent greeting request is made with CTI_PORT_DN as the "IVR call" destination
// 4. CTI_PORT_DN answers the IVR call, plays an audio message, and drops the call
// 5. ALICE_DN drops the original call

// Be sure to rename .env.example to .env and configure your CUCM/user/DN
//   details for the scenario.

// Tested using:
//   Ubuntu Linux 24.04
//   OpenJDK openjdk 11.0.25
//   CUCM 15

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import javax.telephony.*;
import javax.telephony.callcontrol.CallControlTerminalConnection;
import javax.telephony.callcontrol.events.CallCtlTermConnRingingEv;

import com.cisco.jtapi.extensions.*;

import io.github.cdimascio.dotenv.Dotenv;

public class agentGreeting {

    // Retrieve environment variables from .env, if present
    public static Dotenv dotenv = Dotenv.load();

    private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

    public static void log(String msg) {
        System.out.println(dtf.format(LocalDateTime.now()) + " " + msg);
    }

    // The handler classes provide observers for provider/address/terminal/call
    // events
    public static CtiPortHandler ctipHandler = new CtiPortHandler();

    public static CiscoAddress agentAddress;
    public static CiscoTerminal agentTerminal;
    public static CiscoAddress ctipAddress;
    public static CiscoTerminal ctipTerminal;

    public static int ctipRtpPort;
    public static CiscoProvider provider;

    public static CallCtlTermConnRingingEv agentDialinCallEvent;

    public static void main(String[] args)
            throws JtapiPeerUnavailableException,
            InvalidArgumentException,
            ResourceUnavailableException,
            MethodNotSupportedException,
            PrivilegeViolationException,
            InvalidPartyException,
            InvalidStateException,
            CiscoRegistrationException,
            InterruptedException {

        // Create the JtapiPeer object, representing the JTAPI library
        log("Initializing Jtapi");
        CiscoJtapiPeer peer = (CiscoJtapiPeer) JtapiPeerFactory.getJtapiPeer(null);

        // Create and open the Provider, representing a JTAPI connection to CUCM CTI
        // Manager
        String providerString = String.format("%s;login=%s;passwd=%s", dotenv.get("CUCM_ADDRESS"),
                dotenv.get("JTAPI_USERNAME"), dotenv.get("JTAPI_PASSWORD"));
        log("Connecting Provider: " + providerString);
        provider = (CiscoProvider) peer.getProvider(providerString);
        Handler handler = new Handler();
        provider.addObserver(handler);
        log("Awaiting ProvInServiceEv...");
        handler.providerInService.waitTrue();

        CtiPortThread ctiPortThread = new CtiPortThread();
        ctiPortThread.start();

        // Open the ALICE_DN Address and wait for it to go in service
        log("Opening agentAddress DN: " + dotenv.get("ALICE_DN"));
        agentAddress = (CiscoAddress) provider.getAddress(dotenv.get("ALICE_DN"));
        log("Awaiting CiscoAddrInServiceEv for: " + agentAddress.getName() + "...");
        agentAddress.addObserver(handler);
        handler.agentAddressInService.waitTrue();
        // Add a call observer to receive call events
        agentAddress.addCallObserver(handler);
        // Get/open the first Terminal for the Address. Could be multiple
        // if it's a shared line
        agentTerminal = (CiscoTerminal) agentAddress.getTerminals()[0];
        log("Awaiting CiscoTermInServiceEv for: " + agentTerminal.getName() + "...");
        agentTerminal.addObserver(handler);
        handler.agentTerminalInService.waitTrue();

        // Wait for incoming call to ALICE_DN
        log("Awaiting dialin CallCtlTermConnRingingEv for: " + agentTerminal.getName() + "...");
        handler.agentCallRinging.waitTrue();

        // Via the newly populated agentDialinCallEvent (see handler), drill/cast down
        // to
        // a CallControlTerminalConnection so we can do some operations
        CallControlTerminalConnection agentIncomingCctConnection = (CallControlTerminalConnection) agentDialinCallEvent
                .getTerminalConnection();

        CiscoTerminalConnection agentTerminalConnection = (CiscoTerminalConnection) agentDialinCallEvent
                .getTerminalConnection();

        // Answer the agent dialin call
        log("Answering dialin call from DN: " + agentDialinCallEvent.getCallingAddress().getName());
        agentIncomingCctConnection.answer();
        // log("Awaiting CallCtlTermConnTalkingEv for: " + agentTerminal.getName() +
        // "...");
        // handler.agentCallTalking.waitTrue();
        log("Awaiting CiscoRTPOutputStartedEv for: " + agentIncomingCctConnection.getTerminal().getName());
        handler.agentRTPOutputStarted.waitTrue();

        // Starting agent greeting
        agentTerminalConnection.addMediaStream("8000", "1000");

        // Wait for the IVR media stream to end, drop call
        log("Awaiting CiscoMediaStreamEndedEv for: " + agentIncomingCctConnection.getTerminal().getName());
        handler.agentMediaStreamEnded.waitTrue();

        log("Dropping call");
        if (agentAddress.getAddressCallInfo(agentTerminal).getCalls().length > 0)
            agentAddress.getAddressCallInfo(agentTerminal).getCalls()[0].drop();

        log("Done.");
        System.exit(0);
    }

}
