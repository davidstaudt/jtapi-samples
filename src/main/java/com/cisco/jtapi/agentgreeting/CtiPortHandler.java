package com.cisco.jtapi.agentgreeting;

import javax.telephony.*;
import javax.telephony.events.*;
import javax.telephony.callcontrol.*;
import javax.telephony.callcontrol.events.CallCtlTermConnRingingEv;

import com.cisco.jtapi.extensions.*;

import com.cisco.cti.util.Condition;

public class CtiPortHandler implements
        TerminalObserver,
        AddressObserver,
        CallControlCallObserver {

    public Condition ctipAddressInService = new Condition();
    public Condition ctipTerminalInService = new Condition();
    public Condition ctipCallRinging = new Condition();
    public Condition ctipRTPOutputStarted = new Condition();

    void log(String msg) { agentGreeting.log(msg);};

    public void terminalChangedEvent(TermEv[] events) {
        for (TermEv ev : events) {
            log("    (CtiPortThread)Received--> Terminal/" + ev);
            switch (ev.getID()) {
                case CiscoTermInServiceEv.ID:
                    ctipTerminalInService.set();
                    break;
                case CiscoRTPOutputStartedEv.ID:
                CtiPortThread.ctipRTPOutputStartedEvent = (CiscoRTPOutputStartedEv) ev;
                    ctipRTPOutputStarted.set();
                    break;
            }
        }
    }

    public void addressChangedEvent(AddrEv[] events) {
        for (AddrEv ev : events) {
            log("    (CtiPortThread)Received--> Address/" + ev);
            switch (ev.getID()) {
                case CiscoAddrInServiceEv.ID:
                    ctipAddressInService.set();
                    break;
            }
        }
    }

    public void callChangedEvent(CallEv[] events) {
        for (CallEv ev : events) {
            log("    (CtiPortThread)Received--> Call/" + ev);
            switch (ev.getID()) {
                case CallCtlTermConnRingingEv.ID:
                    CtiPortThread.ctipDialinCallEvent = (CallCtlTermConnRingingEv) ev;
                    ctipCallRinging.set();
                    break;
            }
        }
    }

}