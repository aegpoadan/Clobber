package clobber;

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Random;

import game.*;

public class AlphaBetaClobberPlayer extends GamePlayer {

	public class ScoredClobberMove extends ClobberMove 
				 implements Comparable<ScoredClobberMove> {
		
		public double score;
		
		@Override
		public int compareTo(ScoredClobberMove mv) {
			// TODO Auto-generated method stub
			double diff = this.score - mv.score;
			if (diff > 0) {
				return 1;
			} else if (diff < 0) {
				return -1;
			} else {
				return 0;
			}
		}
		
		public ScoredClobberMove() {
			super();
		}
		public ScoredClobberMove(int r1, int c1, int r2, int c2) {
			row1 = r1;
			col1 = c1;
			row2 = r2;
			col2 = c2;
		}
		public void set(ClobberMove mv, double s)
		{
			row1 = mv.row1;
			col1 = mv.col1;
			row2 = mv.row2;
			col2 = mv.col2;
			score = s;
		}
		public void set(double s) {
			score = s;
		}
	}
	
	private class Block {
		public int homeCount;
		public int awayCount;
		public int homeScore;
		public int awayScore;
		
		public Block() {
			homeCount = 0;
			awayCount = 0;
			homeScore = 0;
			awayScore = 0;
		}
	}

	//hardcode
	public static final int MAX_SCORE = 10000;
	public static final int DEPTH_BASE = 8;
	public static final int DEPTH_FACTOR = 3;
	private ScoredClobberMove[] mvStack;
	private int depthLimit;
	private boolean directPrune;
	private int[][][] keyTable;
	private Hashtable<Integer, char[][]> TransTable;
	
	// Performance evaluation use
	private int maxDepthReached;
		
	private void computeKeys() {
		Random rand = new Random();
		for (int r = 0; r < ClobberState.ROWS; r++) {
			for (int c = 0; c < ClobberState.COLS; c++) {
				for (int i = 0; i < GameState.Who.values().length; i++) {
					keyTable[i][r][c] = rand.nextInt(0xFFFF);
				}
			}
		}
	}
	
	public AlphaBetaClobberPlayer(String n) {
		super(n, new ClobberState(), false);
	}
	
	@Override
	public GameMove getMove(GameState state, String lastMv) {
		// Performance evaluation use
		maxDepthReached = 0;
		
		//computeKeys();
		calcDepth(state.numMoves);
		alphaBeta((ClobberState)state, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
		if (maxDepthReached < depthLimit)
			directPrune = false;
		System.out.println("Move score: " + mvStack[0].score 
				+ "\n" + "Depth Reached: " + (maxDepthReached+1) + "\n");
		return mvStack[0];
	}
	
	private void calcDepth(int numMoves) {
		// Calculate the depth limit of AB 
		// by giving number of moves made on board
		depthLimit = (numMoves/2)*(numMoves/2)/DEPTH_FACTOR + DEPTH_BASE;
	}
	
	private char hasPawn(char[][] board, int r, int c) {
		if (Util.inrange(r, ClobberState.ROWS-1) && Util.inrange(c, ClobberState.COLS-1)) {
			return board[r][c];
		}
		return ' ';
	}
	
	private boolean hasBlockNum(int[][] whichBlock, int r, int c) {
		if (Util.inrange(r, ClobberState.ROWS-1) && Util.inrange(c, ClobberState.COLS-1)) {
			if (whichBlock[r][c] != 0)
				return true;
		}
		return false;
	}
	
	private void checkAdj2(int r, int c, char who, Block block, char[][] board) {
		// If adjacent pawn is opponent, we increment the score.
		
		char pieceLeft = hasPawn(board, r, c-1);
		char pieceRight = hasPawn(board, r, c+1);
		char pieceUp = hasPawn(board, r-1, c);
		char pieceDown = hasPawn(board, r+1, c);
		
		if (who == ClobberState.homeSym) {
			if (pieceLeft == ClobberState.awaySym) 
				block.homeScore++;
			if (pieceRight == ClobberState.awaySym) 
				block.homeScore++;
			if (pieceUp == ClobberState.awaySym) 
				block.homeScore++;
			if (pieceDown == ClobberState.awaySym) 
				block.homeScore++;
		} else {
			if (pieceLeft == ClobberState.homeSym) 
				block.awayScore++;
			if (pieceRight == ClobberState.homeSym) 
				block.awayScore++;
			if (pieceUp == ClobberState.homeSym) 
				block.awayScore++;
			if (pieceDown != ClobberState.homeSym) 
				block.awayScore++;
		}
	}
	
	private void checkAdj(int r, int c, char who, Block block, char[][] board) {
		// whatever the adjacent pawn is, we increment the score.
		// also check diagonal allies
		
		char pieceLeft = hasPawn(board, r, c-1);
		char pieceRight = hasPawn(board, r, c+1);
		char pieceUp = hasPawn(board, r-1, c);
		char pieceDown = hasPawn(board, r+1, c);
		
		if (who == ClobberState.homeSym) {
			boolean[] diagonalChecked = new boolean[4];
			if (pieceLeft == ClobberState.homeSym) {
				block.homeScore++;
			} else if (pieceLeft == ClobberState.awaySym) {
				block.homeScore++;
				if (hasPawn(board,r-1,c-1) == ClobberState.homeSym){
					block.homeScore++;
					diagonalChecked[0] = true;
				}
				if (hasPawn(board,r+1,c-1) == ClobberState.homeSym) {
					block.homeScore++;
					diagonalChecked[1] = true;
				}
			}
			if (pieceRight == ClobberState.homeSym) {
				block.homeScore++;
			} else if (pieceRight == ClobberState.awaySym) { 
				block.homeScore++;
				if (hasPawn(board,r-1,c+1) == ClobberState.homeSym){
					block.homeScore++;
					diagonalChecked[2] = true;
				}
				if (hasPawn(board,r+1,c+1) == ClobberState.homeSym) {
					block.homeScore++;
					diagonalChecked[3] = true;
				}
			}
			if (pieceUp == ClobberState.homeSym) {
				block.homeScore++;
			} else if (pieceUp == ClobberState.awaySym) { 
				block.homeScore++;
				if (hasPawn(board,r-1,c-1) == ClobberState.homeSym && !diagonalChecked[0])
					block.homeScore++;
				if (hasPawn(board,r-1,c+1) == ClobberState.homeSym && !diagonalChecked[2])
					block.homeScore++;
			}
			if (pieceDown == ClobberState.homeSym) {
				block.homeScore++;
			} else if (pieceDown == ClobberState.awaySym) { 
				block.homeScore++;
				if (hasPawn(board,r+1,c-1) == ClobberState.homeSym && !diagonalChecked[1])
					block.homeScore++;
				if (hasPawn(board,r+1,c+1) == ClobberState.homeSym && !diagonalChecked[3])
					block.homeScore++;
			}
		} else {
			boolean[] diagonalChecked = new boolean[4];
			if (pieceLeft == ClobberState.awaySym) {
				block.awayScore++;
			} else if (pieceLeft == ClobberState.homeSym) {
				block.awayScore++;
				if (hasPawn(board,r-1,c-1) == ClobberState.awaySym){
					block.awayScore++;
					diagonalChecked[0] = true;
				}
				if (hasPawn(board,r+1,c-1) == ClobberState.awaySym) {
					block.awayScore++;
					diagonalChecked[1] = true;
				}
			}
			if (pieceRight == ClobberState.awaySym) {
				block.awayScore++;
			} else if (pieceRight == ClobberState.homeSym) { 
				block.awayScore++;
				if (hasPawn(board,r-1,c+1) == ClobberState.awaySym){
					block.awayScore++;
					diagonalChecked[2] = true;
				}
				if (hasPawn(board,r+1,c+1) == ClobberState.awaySym) {
					block.awayScore++;
					diagonalChecked[3] = true;
				}
			}
			if (pieceUp == ClobberState.awaySym) {
				block.awayScore++;
			} else if (pieceUp == ClobberState.homeSym) { 
				block.awayScore++;
				if (hasPawn(board,r-1,c-1) == ClobberState.awaySym && !diagonalChecked[0])
					block.awayScore++;
				if (hasPawn(board,r-1,c+1) == ClobberState.awaySym && !diagonalChecked[2])
					block.awayScore++;
			}
			if (pieceDown == ClobberState.awaySym) {
				block.awayScore++;
			} else if (pieceDown == ClobberState.homeSym) { 
				block.awayScore++;
				if (hasPawn(board,r+1,c-1) == ClobberState.awaySym && !diagonalChecked[1])
					block.awayScore++;
				if (hasPawn(board,r+1,c+1) == ClobberState.awaySym && !diagonalChecked[3])
					block.awayScore++;
			}
		}
	}
	
	private double eval(char[][] board) {
		int blockAssigned = 0;
		double score = 0;
		Block[] blocks = new Block[ClobberState.ROWS*ClobberState.COLS/2 + 1];
		int[][] whichBlock = new int[ClobberState.ROWS][ClobberState.COLS];
		
		for (int r = 0; r < ClobberState.ROWS; r++) {
			for (int c = 0; c < ClobberState.COLS; c++) {
				char who = board[r][c];
				if (who != ClobberState.emptySym) {
					int blockNum = 0;
					if (hasBlockNum(whichBlock, r-1, c))
						blockNum = whichBlock[r-1][c];
					else if (hasBlockNum(whichBlock, r+1, c))
						blockNum = whichBlock[r+1][c];
					else if (hasBlockNum(whichBlock, r, c-1))
						blockNum = whichBlock[r][c-1];
					else if (hasBlockNum(whichBlock, r, c+1))
						blockNum = whichBlock[r][c+1];
					if (blockNum == 0) {
						blockAssigned++;
						blockNum = blockAssigned;
						blocks[blockNum] = new Block();
					}
					whichBlock[r][c] = blockNum;
					
					if (who == ClobberState.homeSym)
						blocks[blockNum].homeCount++;
					else
						blocks[blockNum].awayCount++;
					checkAdj(r, c , who, blocks[blockNum], board);
				}
			}
		}
		
		for (int i = 1; i <= blockAssigned; i++) {
			Block b = blocks[i];
			if (b.homeCount == 0 || b.awayCount == 0)
				continue;
			if (b.homeCount >= b.awayCount) {
				score += ((double)b.homeCount)/((double)b.awayCount)
						 *(b.homeCount-b.awayCount+b.homeScore-b.awayScore);
			} else {
				score += ((double)b.awayCount)/((double)b.homeCount)
						 *(b.homeCount-b.awayCount+b.homeScore-b.awayScore);
			}
		}
		return score;
	}
	
	private double evalBoard(ClobberState board) {
		double score = eval(board.board);
		if (Math.abs(score) > MAX_SCORE) {
			System.err.println("Problem with eval");
			System.exit(0);
		}
		return score;
	}
	
	@Override
	public void init()
	{
		depthLimit = DEPTH_BASE;
		directPrune = true;
		int maxMoves = ClobberState.ROWS*ClobberState.COLS*2 
					 - ClobberState.ROWS-ClobberState.COLS;
		mvStack = new ScoredClobberMove[maxMoves];
		for (int i=0; i<maxMoves; i++) {
			mvStack[i] = new ScoredClobberMove();
		}
		/*
		keyTable = new int[GameState.Who.values().length]
				[ClobberState.ROWS][ClobberState.COLS];
		TransTable = new Hashtable<Integer, char[][]>(0xFFFF);
		*/
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

	private static void reOrder(ScoredClobberMove[] mvArray, int count,
			boolean isToMax) {
		if (isToMax)
			Arrays.sort(mvArray, 0, count - 1, Collections.reverseOrder());
		else
			Arrays.sort(mvArray, 0, count - 1);
	}
	
	private void undoMove (ClobberState board, ScoredClobberMove mv) {
		board.board[mv.row1][mv.col1] = board.board[mv.row2][mv.col2];
		board.board[mv.row2][mv.col2] = board.board[mv.row2][mv.col2] == ClobberState.homeSym
										? ClobberState.awaySym : ClobberState.homeSym;
		board.numMoves--;
		board.status = GameState.Status.GAME_ON;
		board.togglePlayer();
	}
	
	private int addValidMove (ClobberState board, ScoredClobberMove mv, 
							   ScoredClobberMove[] mvArray, int index) {
		if (board.moveOK(mv)) {
			board.makeMove(mv);
			mv.set(evalBoard(board));
			undoMove(board, mv);
			mvArray[index] = mv;
			return index+1;
		}
		return index;
	}
	
	private int createMoveArray(ClobberState board, ScoredClobberMove[] moveArray) {
		
		int moveCount = 0;
		for (int r = 0; r < ClobberState.ROWS; r++) {
			for (int c = 0; c < ClobberState.COLS; c++) {
				ScoredClobberMove moveUp = new ScoredClobberMove(r,c,r+1,c);
				ScoredClobberMove moveDown = new ScoredClobberMove(r,c,r-1,c);
				ScoredClobberMove moveLeft = new ScoredClobberMove(r,c,r,c-1);
				ScoredClobberMove moveRight = new ScoredClobberMove(r,c,r,c+1);
				moveCount = addValidMove(board, moveUp, moveArray, moveCount);
				moveCount = addValidMove(board, moveDown, moveArray, moveCount);
				moveCount = addValidMove(board, moveLeft, moveArray, moveCount);
				moveCount = addValidMove(board, moveRight, moveArray, moveCount);
			}
		}
		return moveCount;
	}
	
	private void alphaBeta(ClobberState board, int currDepth, double alpha, double beta) {

		boolean toMaximize = (board.getWho() == GameState.Who.HOME);
		boolean isTerminal = terminalValue(board, mvStack[currDepth]);
		
		if (isTerminal) {
			;
		} else if (currDepth == depthLimit) {
			mvStack[currDepth].set(evalBoard(board));
		} else {
			ScoredClobberMove nextMove = mvStack[currDepth+1];
			ScoredClobberMove bestMove = mvStack[currDepth];
			double bestScore = (toMaximize ? 
					Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
			bestMove.set(bestScore);
			
			// Create a move array with one level down forward checking
			ScoredClobberMove[] mvArray = new ScoredClobberMove
					[ClobberState.ROWS*ClobberState.COLS*2-board.numMoves*2];
			int moveCount = createMoveArray(board, mvArray);
			reOrder(mvArray, moveCount, toMaximize);

			// Performance evaluation use
			if (currDepth == 0)
				System.out.println("Total moves possible: " + moveCount);
			maxDepthReached = Math.max(currDepth, maxDepthReached);
					
			// Prune last 10%, can be risky
			//if (directPrune)
			//	moveCount = moveCount*9/10;
			for (int i = 0; i < moveCount; i++) {
				ScoredClobberMove mv = mvArray[i];
				board.makeMove(mv);
				/*
				int hashKey = 0;
				for (int r = 0; r < ClobberState.ROWS; r++) {
					for (int c = 0; c < ClobberState.COLS; c++) {
						int who = board.board[r][c] == ClobberState.homeSym?1:0;
						hashKey ^= keyTable[who][r][c];  
					}
				}
				TransTable.put(hashKey, board.board);
				*/
				alphaBeta(board, currDepth + 1, alpha, beta); // Check out move
				undoMove(board, mv);

				// Check out the results, update Max/Min nodes
				if (toMaximize && nextMove.score > bestMove.score)
					bestMove.set(mv, nextMove.score);
				else if (!toMaximize && nextMove.score < bestMove.score)
					bestMove.set(mv, nextMove.score);

				// Update alpha and beta. Perform pruning, if possible.
				if (toMaximize) {
					alpha = Math.max(bestMove.score, alpha);
					if (bestMove.score >= beta || bestMove.score == MAX_SCORE)
						return;
				} else {
					beta = Math.min(bestMove.score, beta);
					if (bestMove.score <= alpha || bestMove.score == -MAX_SCORE)
						return;
				}
			}
		}
	}
	
	public static void main(String [] args)
	{
		GamePlayer p = new AlphaBetaClobberPlayer("Alpha-Beta");
		p.compete(args);
	}

}
