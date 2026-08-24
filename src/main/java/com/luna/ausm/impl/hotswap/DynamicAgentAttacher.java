package com.luna.ausm.impl.hotswap;

import com.sun.tools.attach.VirtualMachine;

/** Small command-line bridge for loading the one-shot injector agent. */
public final class DynamicAgentAttacher {
    private DynamicAgentAttacher() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Usage: <pid> <agent-jar> <comma-separated-class-names>");
        }
        VirtualMachine vm = VirtualMachine.attach(arguments[0]);
        try {
            vm.loadAgent(arguments[1], arguments[2]);
        } finally {
            vm.detach();
        }
    }
}
