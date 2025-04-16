package cz.amuradon.tralon.agent.connector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.InputDecorator;

public class MyInputDecorator extends InputDecorator {

	@Override
	public InputStream decorate(IOContext ctxt, InputStream in) throws IOException {
		System.out.println("*** JSON input: InputStream ***");
		return in;
	}

	@Override
	public InputStream decorate(IOContext ctxt, byte[] src, int offset, int length) throws IOException {
		System.out.println("*** JSON input: byte[] ***");
		System.out.println(new String(src));
		return new ByteArrayInputStream(src, offset, length);
	}

	@Override
	public Reader decorate(IOContext ctxt, Reader r) throws IOException {
		System.out.println("*** JSON input: Reader ***");
		return r;
	}
	
}