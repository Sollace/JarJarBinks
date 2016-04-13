package com.blazeloader.jarjar;

import java.io.IOException;
import java.io.OutputStream;

public class MirroredOutputStream extends OutputStream {
	
	private final OutputStream a;
	private final OutputStream b;
	
	public MirroredOutputStream(OutputStream one, OutputStream two) {
		a = one;
		b = two;
	}
	
	@Override
	public void write(int b) throws IOException {
		this.a.write(b);
		this.b.write(b);
	}
	
	public void flush() throws IOException {
		this.a.flush();
		this.b.flush();
    }
	
	public void close() throws IOException {
		this.a.close();
		this.b.close();
    }
}
