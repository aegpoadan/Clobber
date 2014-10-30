package clobber;

import java.util.ArrayList;

import game.*;

public class AlphaBetaClobberPlayer extends GamePlayer implements Runnable {
	private static ArrayList<AlphaBetaClobberPlayer> players = new ArrayList<AlphaBetaClobberPlayer>();
	private static ArrayList<Thread> threadPool = new ArrayList<Thread>();
	private static ArrayList<ClobberState> states = new ArrayList<ClobberState>();
	private ClobberState bestState;
	private int threadId;
	private static int numCores = Runtime.getRuntime().availableProcessors();
	private boolean fresh;
	private static int stateIndex;
	private static int bestThread;
	private static boolean multiThreaded = true;

	public class ScoredClobberMove extends ClobberMove {
		public ScoredClobberMove() {
			super();
		}
		public ScoredClobberMove(int r1, int c1, int r2, int c2, double s) {
			row1 = r1;
			col1 = c1;
			row2 = r2;
			col2 = c2;
			score = s;
		}
		
		public ScoredClobberMove(ScoredClobberMove m)
		{
			super(m.row1, m.col1, m.row2, m.col2);
			score = m.score;
		}
		public void set(int r1, int c1, int r2, int c2, double s)
		{
			row1 = r1;
			col1 = c1;
			row2 = r2;
			col2 = c2;
			score = s;
		}
		public void set(double s) {
			score = s;
		}
		public double score;
	}

	//hardcode
	public static final int MAX_SCORE = 10000;
	public static final int MAX_DEPTH = 50;
	private ScoredClobberMove[] mvStack;
	public int depthLimit = 6;
	
	public AlphaBetaClobberPlayer(String n) {
		super(n, new ClobberState(), false);
	}
	
	//When multi-threading, each thread needs its own gameState and mvStack
	public AlphaBetaClobberPlayer(String n, GameState gs, ScoredClobberMove[] mvStack, int threadId, boolean fresh) {
		super(n, gs, false);
		this.threadId = threadId;
		this.fresh = fresh;
		this.mvStack = mvStack;
	}
	
	@SuppressWarnings("finally")
	@Override
	public GameMove getMove(GameState state, String lastMv) {
		ArrayList<AlphaBetaClobberPlayer> z = players;
		clearThreads();
		if (multiThreaded) {
			expandNode((ClobberState)state);
			try {
				runThreads((ClobberState)state);
				System.out.println(players.get(bestThread).mvStack[0].score);
				return players.get(bestThread).mvStack[0];
			} catch (InterruptedException e) {
				System.err.println("A thread was interrupted!");
				System.err.println("Error running multi-threaded version!");
				clearThreads();
				e.printStackTrace();
			} 
		} else {
			alphaBeta((ClobberState)state, 0, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
			System.out.println(mvStack[0].score);
			return mvStack[0];
		}
		return null;
	}
	
	private static boolean hasPawn(ClobberState board, char who,
										 int r, int c) {
		if (Util.inrange(r, ClobberState.ROWS-1) && Util.inrange(c, ClobberState.COLS-1)) {
			if (board.board[r][c] == who) {
				return true;
			}
		}
		return false;
	}
	
	private static int checkDiagonals (ClobberState board, char who, int score,
						   int r, int c) {
		// Check to see there're allied pawns at diagonals
		
		score++;
		score = hasPawn(board,who,r-1,c-1)?score+1:score;
		score = hasPawn(board,who,r+1,c-1)?score+1:score;
		score = hasPawn(board,who,r+1,c+1)?score+1:score;
		score = hasPawn(board,who,r-1,c+1)?score+1:score;
		return score;
	}
	
	private static int eval(ClobberState board, char who) {
		int score = 0;
		int count = 0;
		boolean valid = false;
		char opponent = who == ClobberState.homeSym 
						? ClobberState.awaySym : ClobberState.homeSym;
		for (int r = 0; r < board.ROWS; r++) {
			for (int c = 0; c < board.COLS; c++) {
				valid = false;
				if (board.board[r][c] == who) {
					if (hasPawn(board, opponent, r-1, c)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					}
					if (hasPawn(board, opponent, r+1, c)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					}
					if (hasPawn(board, opponent, r, c+1)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					} 
					if (hasPawn(board, opponent, r, c-1)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					}
					if (valid) {
						score++;
						count++;
					}
				}
			}
		}
		return score;
	}
	
	private static int evalBoard(ClobberState board) {
		int score = eval(board, ClobberState.homeSym) - eval(board, ClobberState.awaySym);
		if (Math.abs(score) > MAX_SCORE) {
			System.err.println("Problem with eval");
			System.exit(0);
		}
		return score;
	}
	
	@Override
	public void init()
	{
		mvStack = new ScoredClobberMove[MAX_DEPTH];
		for (int i=0; i<MAX_DEPTH; i++) {
			mvStack[i] = new ScoredClobberMove();
		}
	}
	
	private boolean terminalValue(GameState brd, ScoredClobberMove mv)
	{
		GameState.Status status = brd.getStatus();
		boolean isTerminal = true;
		
		if (status == GameState.Status.HOME_WIN) {
			mv.set(MAX_SCORE);
		} else if (status == GameState.Status.AWAY_WIN) {
			mv.set(-MAX_SCORE);
		} else {
			isTerminal = false;
		}
		return isTerminal;
	}

	private static void shuffle(ScoredClobberMove[] ary, int count)
	{
		for (int i=0; i<count; i++) {
			int spot = Util.randInt(i, count-1);
			ScoredClobberMove tmp = ary[i];
			ary[i] = ary[spot];
			ary[spot] = tmp;
		}
	}

	
	private void alphaBeta(ClobberState board, int currDepth, double alpha, double beta) {

		boolean toMaximize = (board.getWho() == GameState.Who.HOME);
		boolean toMinimize = !toMaximize;
		boolean isTerminal = terminalValue(board, mvStack[currDepth]);
		
		if (isTerminal) {
			;
		} else if (currDepth == depthLimit) {
			mvStack[currDepth].set(evalBoard(board));
		} else {
			double bestScore = (toMaximize ? 
					Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
			ScoredClobberMove bestMove = mvStack[currDepth];
			ScoredClobberMove nextMove = mvStack[currDepth+1];

			bestMove.set(bestScore);
			GameState.Who currTurn = board.getWho();

			ScoredClobberMove[] moveArray 
				= new ScoredClobberMove[ClobberState.ROWS*ClobberState.COLS*2 - board.numMoves*2];
			int moveCount = 0;
			for (int r=0; r<ClobberState.ROWS; r++) {
				for (int c = 0; c < ClobberState.COLS; c++) {
					ScoredClobberMove moveUp = new ScoredClobberMove(r,c,r+1,c,0);
					if (board.moveOK(moveUp)) {
						moveArray[moveCount] = moveUp;
						moveCount++;
					}
					ScoredClobberMove moveDown = new ScoredClobberMove(r,c,r-1,c,0);
					if (board.moveOK(moveDown)) {
						moveArray[moveCount] = moveDown;
						moveCount++;					
					}
					ScoredClobberMove moveLeft = new ScoredClobberMove(r,c,r,c-1,0);
					if (board.moveOK(moveLeft)) {
						moveArray[moveCount] = moveLeft;
						moveCount++;					
					}
					ScoredClobberMove moveRight = new ScoredClobberMove(r,c,r,c+1,0);
					if (board.moveOK(moveRight)) {
						moveArray[moveCount] = moveRight;
						moveCount++;	
					}			
				}
			}
			shuffle(moveArray, moveCount);
					
			
			for (int i = 0; i < moveCount; i++) {
				ScoredClobberMove mv = moveArray[i];
				boolean moveOK = board.makeMove(mv);
				if (!moveOK)
					System.out.println("wtf");

				alphaBeta(board, currDepth + 1, alpha, beta); // Check out move

				// Undo move
				board.board[mv.row1][mv.col1] = board.board[mv.row2][mv.col2];
				board.board[mv.row2][mv.col2] = board.board[mv.row2][mv.col2] == ClobberState.homeSym
												? ClobberState.awaySym : ClobberState.homeSym;
				board.numMoves--;
				board.status = GameState.Status.GAME_ON;
				board.who = currTurn;

				// Check out the results, relative to what we've seen before
				if (toMaximize && nextMove.score > bestMove.score) {
					bestMove = mv;
					bestMove.set(nextMove.score);
					mvStack[currDepth] = bestMove;//??????
				} else if (!toMaximize && nextMove.score < bestMove.score) {
					bestMove = mv;
					bestMove.set(nextMove.score);
					mvStack[currDepth] = bestMove;//??????
				}

				// Update alpha and beta. Perform pruning, if possible.
				if (toMinimize) {
					beta = Math.min(bestMove.score, beta);
					if (bestMove.score <= alpha || bestMove.score == -MAX_SCORE) {
						return;
					}
				} else {
					alpha = Math.max(bestMove.score, alpha);
					if (bestMove.score >= beta || bestMove.score == MAX_SCORE) {
						return;
					}
				}
			}
		}

	}
	
	private void expandNode(ClobberState board) {
		ScoredClobberMove[] moveArray 
		= new ScoredClobberMove[ClobberState.ROWS*ClobberState.COLS*2 - board.numMoves*2];
		
		int moveCount = 0;
		for (int r=0; r<ClobberState.ROWS; r++) {
			for (int c = 0; c < ClobberState.COLS; c++) {
				ScoredClobberMove moveUp = new ScoredClobberMove(r,c,r+1,c,0);
				if (board.moveOK(moveUp)) {
					moveArray[moveCount] = moveUp;
					moveCount++;
				}
				ScoredClobberMove moveDown = new ScoredClobberMove(r,c,r-1,c,0);
				if (board.moveOK(moveDown)) {
					moveArray[moveCount] = moveDown;
					moveCount++;					
				}
				ScoredClobberMove moveLeft = new ScoredClobberMove(r,c,r,c-1,0);
				if (board.moveOK(moveLeft)) {
					moveArray[moveCount] = moveLeft;
					moveCount++;					
				}
				ScoredClobberMove moveRight = new ScoredClobberMove(r,c,r,c+1,0);
				if (board.moveOK(moveRight)) {
					moveArray[moveCount] = moveRight;
					moveCount++;	
				}			
			}
		}
		shuffle(moveArray, moveCount);
		
		for(int i=0; i<moveCount; i++) {
			ClobberState newBoard = (ClobberState) board.clone();
			boolean okMove = newBoard.makeMove(moveArray[i]);
			ArrayList z = states;
			if(okMove) {
				states.add(newBoard);
			} else {
				System.err.println("Bad move in expandNode");
			}
		}
	}
	
	public static void main(String [] args)
	{
		GamePlayer p = new AlphaBetaClobberPlayer("Alpha-Beta");
		//ClobberState state = new ClobberState();
		//p.getMove(state, "--");
		p.compete(args, 1);
		/*
		p.init();
		CLobberState state = new ClobberState();
		state.makeMove(new Connect4Move(3));
		state.makeMove(new Connect4Move(4));
		state.makeMove(new Connect4Move(4));
		state.makeMove(new Connect4Move(5));
		GameMove mv = p.getMove(state, "");
		System.out.println("Original board");
		System.out.println(state.toString());
		System.out.println("Move: " + mv.toString());
		System.out.println("Board after move");
		state.makeMove(mv);
		System.out.println(state.toString());
		 */
	}

	public void run() {
		this.init();
		while(okToRun()) {
			if(fresh) {
				alphaBeta((ClobberState)super.gameState, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				bestState = (ClobberState)this.gameState;
				fresh = false;
			} else {
				ClobberState checkMe;
				synchronized(states) {
					checkMe = states.get(stateIndex);
					stateIndex++;
				}
				alphaBeta(checkMe, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				bestState = compareStates();
			}
		}
	}
	
	private boolean okToRun() {
		boolean go;
		synchronized(states) {
			if (stateIndex < states.size() - 1)
				go = true;
			else
				go = false;
		}
		return go;
	}
	
	private ClobberState compareStates()  {
		int current = evalBoard(this.bestState); //Best state found so far by this thread
		int other = evalBoard((ClobberState)super.gameState); //State found by most recent alphabeta
		if(current > other)
			return (ClobberState)super.gameState;
		else {
			return this.bestState;
		}
	} 
	
	private static int getBestState() {
		int[] best = new int[2]; //[0] is best score, [1] is threadId
		best[0] = -1;
		best[1] = -1;
		ArrayList z = players;
		for(AlphaBetaClobberPlayer p: players) {
			int score = evalBoard(p.bestState);
			if(score > best[0]) {
				best[0] = score;
				best[1] = p.threadId;
			}
		}
		return best[1];
	}
	
	public static void clearThreads() {
		players.clear();
		threadPool.clear();
		states.clear();
		bestThread = -1;
	}
	
	public static void runThreads(ClobberState gs) throws InterruptedException {
		ArrayList x = players;
		for(int i=0; i<numCores; i++) {
			AlphaBetaClobberPlayer z = new AlphaBetaClobberPlayer("Alpha-Beta", states.get(i), null, i, true);
			players.add(z);
		}
		
		for(GamePlayer p: players) {
			threadPool.add(new Thread((Runnable) p));
		}
		for(Thread t: threadPool) {
			t.start();
		}
		for(Thread t: threadPool) {
			t.join();
		}
		bestThread = getBestState();

	}

}
