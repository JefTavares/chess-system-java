package chess;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import boardgame.Board;
import boardgame.Piece;
import boardgame.Position;
import chess.pieces.Bishop;
import chess.pieces.King;
import chess.pieces.Knight;
import chess.pieces.Pawn;
import chess.pieces.Queen;
import chess.pieces.Rook;

/** A partida e as regras da partida ocorre aqui */
public class ChessMatch {

    private int turn;
    private Color currentPlayer;
    private Board board;
    private boolean check;
    private boolean checkMate;
    private ChessPiece enPassantVulnerable;
    private ChessPiece promoted;

    private List<Piece> piecesOnTheBoard = new ArrayList<>();
    private List<Piece> capturedPieces = new ArrayList<>();

    public ChessMatch() {
        this.board = new Board(8, 8);
        turn = 1;
        currentPlayer = Color.WHITE;
        check = false;
        initialSetup();
    }

    public int getTurn() {
        return turn;
    }

    public Color getCurrentPlayer() {
        return currentPlayer;
    }

    /** Se o game está em check */
    public boolean getCheck() {
        return check;
    }

    public boolean getCheckMate() {
        return checkMate;
    }

    public ChessPiece getEnPassantVulnerable() {
        return enPassantVulnerable;
    }

    public ChessPiece getPromoted() {
        return promoted;
    }

    public ChessPiece[][] getPieces() {
        ChessPiece[][] mat = new ChessPiece[board.getRows()][board.getColumns()];

        for (int i = 0; i < board.getRows(); i++) {
            for (int j = 0; j < board.getColumns(); j++) {
                mat[i][j] = (ChessPiece) board.piece(i, j); // downcasting para ChessPiece
            }
        }
        return mat;
    }

    public boolean[][] possibleMoves(ChessPosition sourcePosition) {
        Position position = sourcePosition.toPosition();
        validateSourcePosition(position);

        return board.piece(position).possibleMoves();

    }

    public ChessPiece performChessMove(ChessPosition sourcePosition, ChessPosition targetPosition) {
        Position source = sourcePosition.toPosition();
        Position target = targetPosition.toPosition();

        validateSourcePosition(source);
        validateTargetPosition(source, target);
        Piece capturedPiece = makeMove(source, target);

        /** Testa joganda, para jogar não se colocar em check */
        if (testCheck(currentPlayer)) {
            undoMove(source, target, capturedPiece);
            throw new ChessException("You can't put yourself in check");
        }

        ChessPiece movedPiece = (ChessPiece) board.piece(target);

        // #Specialmove promotion
        promoted = null;
        if (movedPiece instanceof Pawn) {
            // se foi um peão, testa se chegou até o final
            if (movedPiece.getColor() == Color.WHITE && target.getRow() == 0 ||
                    movedPiece.getColor() == Color.BLACK && target.getRow() == 7) {
                promoted = (ChessPiece) board.piece(target);
                promoted = replacePromotedPiece("Q");
            }
        }

        check = (testCheck(opponent(currentPlayer))) ? true : false;

        if (testCheckMate(opponent(currentPlayer))) {
            checkMate = true;
        } else {
            nextTurn();
        }

        // #specialmove en passant
        if (movedPiece instanceof Pawn
                && (target.getRow() == source.getRow() - 2 || target.getRow() == source.getRow() + 2)) {
            // peão vulneravel a tomar en passant no proximo turno
            enPassantVulnerable = movedPiece;
        } else {
            enPassantVulnerable = null;
        }

        return (ChessPiece) capturedPiece;
    }

    public ChessPiece replacePromotedPiece(String type) {
        if (promoted == null) {
            throw new IllegalStateException("There is no piece to be promoted");
        }

        if (!type.equals("B") && !type.equals("N")
                && !type.equals("R")
                && !type.equals("Q")) {
            return promoted;
        }

        Position pos = promoted.getChessPosition().toPosition();
        Piece p = board.removePiece(pos);
        piecesOnTheBoard.remove(p);

        ChessPiece newPiece = newPiece(type, promoted.getColor());
        board.placePiece(newPiece, pos);
        piecesOnTheBoard.add(newPiece);

        return newPiece;
    }

    /** Retonar uma peça chamada no evento promotion */
    private ChessPiece newPiece(String type, Color color) {
        if (type.equals("B"))
            return new Bishop(board, color);
        if (type.equals("N"))
            return new Knight(board, color);
        if (type.equals("Q"))
            return new Queen(board, color);
        return new Rook(board, color);
    }

    private Piece makeMove(Position source, Position target) {
        ChessPiece p = (ChessPiece) board.removePiece(source);
        p.increaseMoveCount();
        Piece capturedPiece = board.removePiece(target);
        board.placePiece(p, target);

        if (capturedPiece != null) {
            piecesOnTheBoard.remove(capturedPiece);
            capturedPieces.add(capturedPiece);
        }

        // #specialmove castling kingside rook
        // se o rei andou duas casa a direita
        if (p instanceof King && target.getColumn() == source.getColumn() + 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() + 3);
            Position targetT = new Position(source.getRow(), source.getColumn() + 1);

            ChessPiece rook = (ChessPiece) board.removePiece(sourceT); // retira a torre
            board.placePiece(rook, targetT); // muda o torre de lugar
            rook.increaseMoveCount();
        }

        // #specialmove castling queenside rook
        // se o rei andou duas casas para a esquerda
        if (p instanceof King && target.getColumn() == source.getColumn() - 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() - 4); // procura a torre
            Position targetT = new Position(source.getRow(), source.getColumn() - 1);

            ChessPiece rook = (ChessPiece) board.removePiece(sourceT); // retira a torre
            board.placePiece(rook, targetT); // muda o torre de lugar
            rook.increaseMoveCount();
        }

        // #specialmove en passant
        if (p instanceof Pawn) {
            // andou na diagonal e não capturou uma peça (foi e passant)
            if (source.getColumn() != target.getColumn() && capturedPiece == null) {
                Position pawnPosition;
                if (p.getColor() == Color.WHITE) {
                    pawnPosition = new Position(target.getRow() + 1, target.getColumn());
                } else {
                    pawnPosition = new Position(target.getRow() - 1, target.getColumn());
                }
                capturedPiece = board.removePiece(pawnPosition);
                capturedPieces.add(capturedPiece);
                piecesOnTheBoard.remove(capturedPiece);
            }
        }

        return capturedPiece;
    }

    private void undoMove(Position source, Position target, Piece capturedPiece) {
        ChessPiece p = (ChessPiece) board.removePiece(target);
        p.decreaseMoveCount();

        board.placePiece(p, source);

        if (capturedPiece != null) {
            board.placePiece(capturedPiece, target);
            capturedPieces.remove(capturedPiece);
            piecesOnTheBoard.add(capturedPiece);
        }

        // #specialmove castling kingside rook
        // se o rei andou duas casa a direita
        if (p instanceof King && target.getColumn() == source.getColumn() + 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() + 3);
            Position targetT = new Position(source.getRow(), source.getColumn() + 1);

            ChessPiece rook = (ChessPiece) board.removePiece(targetT); // retira a torre
            board.placePiece(rook, sourceT); // muda o torre de lugar
            rook.decreaseMoveCount();
        }

        // #specialmove castling queenside rook
        // se o rei andou duas casas para a esquerda
        if (p instanceof King && target.getColumn() == source.getColumn() - 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() - 4); // procura a torre
            Position targetT = new Position(source.getRow(), source.getColumn() - 1);

            ChessPiece rook = (ChessPiece) board.removePiece(targetT); // retira a torre
            board.placePiece(rook, sourceT); // muda o torre de lugar
            rook.decreaseMoveCount();
        }

        // #specialmove en passant
        if (p instanceof Pawn) {
            // andou na diagonal e não capturou uma peça (foi e passant)
            if (source.getColumn() != target.getColumn() && capturedPiece == enPassantVulnerable) {
                ChessPiece pawn = (ChessPiece) board.removePiece(target);
                Position pawnPosition;
                if (p.getColor() == Color.WHITE) {
                    pawnPosition = new Position(3, target.getColumn());
                } else {
                    pawnPosition = new Position(4, target.getColumn());
                }
                board.placePiece(pawn, pawnPosition);
            }
        }

    }

    private void validateSourcePosition(Position position) {
        if (!board.thereIsAPiece(position)) {
            throw new ChessException("There is no piece on sorce position");
        }
        if (currentPlayer != ((ChessPiece) board.piece(position)).getColor()) {
            throw new ChessException("The chosen piece is not yours");
        }
        // Não Tem algum movimento possivel ?
        if (!board.piece(position).isThereAnyPossibleMove()) {
            throw new ChessException("There is no possible moves for the chosen piece");
        }

    }

    private void validateTargetPosition(Position source, Position target) {
        if (!board.piece(source).possibleMove(target)) {
            throw new ChessException("The chosen piece can't move to target position");
        }
    }

    private void nextTurn() {
        turn++;
        currentPlayer = (currentPlayer == Color.WHITE) ? Color.BLACK : Color.WHITE;
    }

    /** Dado uma cor devolve o oponente dessa cor */
    private Color opponent(Color color) {
        return (color == Color.WHITE) ? Color.BLACK : Color.WHITE;
    }

    /** Localiza o rei de uma cor */
    private ChessPiece king(Color color) {
        List<Piece> list = piecesOnTheBoard.stream().filter(x -> ((ChessPiece) x).getColor() == color)
                .collect(Collectors.toList());

        for (Piece p : list) {
            if (p instanceof King) {
                return (ChessPiece) p;
            }
        }
        // Falha que não deve acontecer, se o rei não está em lugar nenhum é porque tem
        // coisa
        throw new IllegalStateException("There is no " + color + " King on the board");
    }

    /** Passando uma cor, verifica se o rei dessa cor está em check */
    private boolean testCheck(Color color) {
        // Pega a posição do rei no formado de matriz
        Position kingPosition = king(color).getChessPosition().toPosition();
        // Pega todas as peças do oponente no tabuleiro
        List<Piece> opponentPieces = piecesOnTheBoard.stream()
                .filter(x -> ((ChessPiece) x).getColor() == opponent(color)).collect(Collectors.toList());

        // Para cada peça do oponente testa os movimentos possiveis dele se 1 dele leva
        // ao rei
        for (Piece p : opponentPieces) {
            boolean[][] mat = p.possibleMoves();
            if (mat[kingPosition.getRow()][kingPosition.getColumn()]) {
                return true;
            }
        }
        return false;
    }

    /** Válida se a partida está em cheque mate */
    private boolean testCheckMate(Color color) {
        if (!testCheck(color)) {
            return false;
        }
        // Pega todas as peças
        List<Piece> list = piecesOnTheBoard.stream().filter(x -> ((ChessPiece) x).getColor() == color)
                .collect(Collectors.toList());

        for (Piece p : list) {
            // Pega os movimentos possiveis da peça p
            boolean[][] mat = p.possibleMoves();

            for (int i = 0; i < board.getRows(); i++) {
                for (int j = 0; j < board.getColumns(); j++) {
                    if (mat[i][j]) {
                        Position source = ((ChessPiece) p).getChessPosition().toPosition();
                        Position target = new Position(i, j);
                        Piece capturedPiece = makeMove(source, target);
                        boolean testCheck = testCheck(color); // verifica se o rei da cor ainda está em check
                        undoMove(source, target, capturedPiece); // volta o movimento
                        if (!testCheck) {
                            return false; // não está em cheque mate
                        }
                    }
                }
            }
        }
        return true;
    }

    // Coloca uma peça passando as posição na coordenadas do xadrez
    private void placeNewPiece(char column, int row, ChessPiece piece) {
        board.placePiece(piece, new ChessPosition(column, row).toPosition());
        piecesOnTheBoard.add(piece);
    }

    private void initialSetup() {

        // board.placePiece(new Rook(board, Color.WHITE), new Position(2, 1));
        // placeNewPiece('b', 6, new Rook(board, Color.WHITE));
        // board.placePiece(new King(board, Color.BLACK), new Position(0, 4));
        // placeNewPiece('e', 8, new King(board, Color.BLACK));
        // board.placePiece(new King(board, Color.WHITE), new Position(7, 4));
        // placeNewPiece('e', 1, new King(board, Color.WHITE));

//		placeNewPiece('c', 1, new Rook(board, Color.WHITE));
//		placeNewPiece('c', 2, new Rook(board, Color.WHITE));
//		placeNewPiece('d', 2, new Rook(board, Color.WHITE));
//		placeNewPiece('e', 2, new Rook(board, Color.WHITE));
//		placeNewPiece('e', 1, new Rook(board, Color.WHITE));
//		placeNewPiece('d', 1, new King(board, Color.WHITE));
//
//		placeNewPiece('c', 7, new Rook(board, Color.BLACK));
//		placeNewPiece('c', 8, new Rook(board, Color.BLACK));
//		placeNewPiece('d', 7, new Rook(board, Color.BLACK));
//		placeNewPiece('e', 7, new Rook(board, Color.BLACK));
//		placeNewPiece('e', 8, new Rook(board, Color.BLACK));
//		placeNewPiece('d', 8, new King(board, Color.BLACK));

//		placeNewPiece('h', 7, new Rook(board, Color.WHITE));
//		placeNewPiece('d', 1, new Rook(board, Color.WHITE));
//		placeNewPiece('e', 1, new King(board, Color.WHITE));
//
//		placeNewPiece('b', 8, new Rook(board, Color.BLACK));
//		placeNewPiece('a', 8, new King(board, Color.BLACK));

        placeNewPiece('a', 1, new Rook(board, Color.WHITE));
        placeNewPiece('b', 1, new Knight(board, Color.WHITE));
        placeNewPiece('c', 1, new Bishop(board, Color.WHITE));
        placeNewPiece('d', 1, new Queen(board, Color.WHITE));
        // Envia o this (a própria ChessMatch que estou trabalhando)
        placeNewPiece('e', 1, new King(board, Color.WHITE, this));
        placeNewPiece('f', 1, new Bishop(board, Color.WHITE));
        placeNewPiece('g', 1, new Knight(board, Color.WHITE));
        placeNewPiece('h', 1, new Rook(board, Color.WHITE));
        placeNewPiece('a', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('b', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('c', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('d', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('e', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('f', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('g', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('h', 2, new Pawn(board, Color.WHITE, this));

        placeNewPiece('a', 8, new Rook(board, Color.BLACK));
        placeNewPiece('b', 8, new Knight(board, Color.BLACK));
        placeNewPiece('c', 8, new Bishop(board, Color.BLACK));
        placeNewPiece('d', 8, new Queen(board, Color.BLACK));
        // Envia o this (a própria ChessMatch que estou trabalhando)
        placeNewPiece('e', 8, new King(board, Color.BLACK, this));
        placeNewPiece('f', 8, new Bishop(board, Color.BLACK));
        placeNewPiece('g', 8, new Knight(board, Color.BLACK));
        placeNewPiece('h', 8, new Rook(board, Color.BLACK));
        placeNewPiece('a', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('b', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('c', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('d', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('e', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('f', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('g', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('h', 7, new Pawn(board, Color.BLACK, this));

    }

}
