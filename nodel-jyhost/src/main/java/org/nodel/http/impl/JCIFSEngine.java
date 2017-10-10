package org.nodel.http.impl;

import java.io.IOException;

import org.apache.http.impl.auth.NTLMEngine;
import org.apache.http.impl.auth.NTLMEngineException;

import jcifs.ntlmssp.NtlmFlags;
import jcifs.ntlmssp.Type1Message;
import jcifs.ntlmssp.Type2Message;
import jcifs.ntlmssp.Type3Message;
import jcifs.util.Base64;

public class JCIFSEngine implements NTLMEngine {
    
    private static final int TYPE_1_FLAGS = NtlmFlags.NTLMSSP_NEGOTIATE_56 | 
                                            NtlmFlags.NTLMSSP_NEGOTIATE_128 | 
                                            NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2 | 
                                            NtlmFlags.NTLMSSP_NEGOTIATE_ALWAYS_SIGN | 
                                            NtlmFlags.NTLMSSP_REQUEST_TARGET;

    public String generateType1Msg(String domain, String workstation) throws NTLMEngineException {
        final Type1Message type1Message = new Type1Message(TYPE_1_FLAGS, domain, workstation);
        return Base64.encode(type1Message.toByteArray());
    }

    public String generateType3Msg(String username, String password, String domain, String workstation, String challenge) throws NTLMEngineException {
        Type2Message type2Message;

        try {
            type2Message = new Type2Message(Base64.decode(challenge));
        } catch (final IOException exception) {
            throw new NTLMEngineException("Error in type2 message", exception);
        }

        final int type2Flags = type2Message.getFlags();
        final int type3Flags = type2Flags & (0xffffffff ^ (NtlmFlags.NTLMSSP_TARGET_TYPE_DOMAIN | NtlmFlags.NTLMSSP_TARGET_TYPE_SERVER));
        final Type3Message type3Message = new Type3Message(type2Message, password, domain, username, workstation, type3Flags);
        return Base64.encode(type3Message.toByteArray());
    }
    
}
