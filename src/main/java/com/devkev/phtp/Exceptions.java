package com.devkev.phtp;

public interface Exceptions {
	
	public class PhasingNotFound extends Exception {
		
		public PhasingNotFound(String string) {
			super(string);
		}

		private static final long serialVersionUID = 1L;
	}
	
	public class PhasingCannotBeOverwritten extends Exception {
		
		public PhasingCannotBeOverwritten(String string) {
			super(string);
		}

		private static final long serialVersionUID = 1L;
	}
}
