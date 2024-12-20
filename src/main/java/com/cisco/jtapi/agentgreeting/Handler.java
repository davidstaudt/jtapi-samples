package com.cisco.jtapi.agentgreeting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.telephony.*;
import javax.telephony.events.*;
import javax.telephony.callcontrol.*;
import javax.telephony.callcontrol.events.CallCtlTermConnRingingEv;

import com.cisco.jtapi.extensions.*;

import com.cisco.cti.util.Condition;

public class Handler implements
        ProviderObserver,
        TerminalObserver,
        AddressObserver,
        CallControlCallObserver {

    private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

    public static void log(String msg) {
        System.out.println(dtf.format(LocalDateTime.now()) + " " + msg);
    }

    public Condition providerInService = new Condition();
    public Condition agentAddressInService = new Condition();
    public Condition agentTerminalInService = new Condition();
    public Condition agentCallRinging = new Condition();
    public Condition agentRTPOutputStarted = new Condition();
    public Condition agentMediaStreamEnded = new Condition();

    public void providerChangedEvent(ProvEv[] events) {
        for (ProvEv ev : events) {
            log("    Received--> Provider/" + ev);
            switch (ev.getID()) {
                case ProvInServiceEv.ID:
                    providerInService.set();
                    break;
            }
        }
    }

    public void terminalChangedEvent(TermEv[] events) {
        for (TermEv ev : events) {
            log("    Received--> Terminal/" + ev + " " + ev.getID());
            switch (ev.getID()) {
                case CiscoTermInServiceEv.ID:
                    agentTerminalInService.set();
                    break;
                case CiscoRTPOutputStartedEv.ID:
                    agentRTPOutputStarted.set();
                    break;
            }
        }
    }

    public void addressChangedEvent(AddrEv[] events) {
        for (AddrEv ev : events) {
            log("    Received--> Address/" + ev);
            switch (ev.getID()) {
                case CiscoAddrInServiceEv.ID:
                    agentAddressInService.set();
                    break;
            }
        }
    }

    public void callChangedEvent(CallEv[] events) {
        for (CallEv ev : events) {
            log("    Received--> Call/" + ev);
            switch (ev.getID()) {
                case CallCtlTermConnRingingEv.ID:
                    agentGreeting.agentDialinCallEvent = (CallCtlTermConnRingingEv) ev;
                    agentCallRinging.set();
                    break;
                case CiscoMediaStreamEndedEv.ID:
                    agentMediaStreamEnded.set();
                    break;
            }
        }
    }
}
