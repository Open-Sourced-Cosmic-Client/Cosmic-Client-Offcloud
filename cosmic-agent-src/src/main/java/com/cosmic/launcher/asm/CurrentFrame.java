package com.cosmic.launcher.asm;

import com.cosmic.launcher.asm.Frame;
import com.cosmic.launcher.asm.Label;
import com.cosmic.launcher.asm.Symbol;
import com.cosmic.launcher.asm.SymbolTable;

final class CurrentFrame
extends Frame {
    CurrentFrame(Label owner) {
        super(owner);
    }

    void execute(int opcode, int arg, Symbol symbolArg, SymbolTable symbolTable) {
        super.execute(opcode, arg, symbolArg, symbolTable);
        Frame successor = new Frame(null);
        this.merge(symbolTable, successor, 0);
        this.copyFrom(successor);
    }
}

