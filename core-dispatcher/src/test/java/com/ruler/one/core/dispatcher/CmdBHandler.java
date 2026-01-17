package com.ruler.one.core.dispatcher;

public class CmdBHandler implements TypeHandler<CmdB> {

    boolean called = false;

    @Override
    public Class<CmdB> supportsType() {
        return CmdB.class;
    }

    @Override
    public void handle(CmdB data) {
        called = true;
    }

}
