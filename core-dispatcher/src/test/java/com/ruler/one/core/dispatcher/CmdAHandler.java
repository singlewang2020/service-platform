package com.ruler.one.core.dispatcher;

public class CmdAHandler implements TypeHandler<CmdA> {

    boolean called = false;

    @Override
    public Class<CmdA> supportsType() {
        return CmdA.class;
    }

    @Override
    public void handle(CmdA data) {
        called = true;
    }

}
