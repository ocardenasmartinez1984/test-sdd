public class SumaMaximaSubmatriz {

    public static int maxSumaSubmatriz2x2(int[][] matriz) {
        // Validación básica: la matriz debe tener al menos 2 filas y 2 columnas
        if (matriz == null || matriz.length < 2 || matriz[0].length < 2) {
            throw new IllegalArgumentException("La matriz debe ser de al menos 2x2.");
        }

        int filas = matriz.length;
        int columnas = matriz[0].length;
        
        // Inicializamos la suma máxima con el valor entero más bajo posible
        int sumaMaxima = Integer.MIN_VALUE;

        // Recorremos la matriz hasta (filas - 1) y (columnas - 1)
        // para asegurar que la ventana de 2x2 no se salga de los límites.
        for (int i = 0; i < filas - 1; i++) {
            for (int j = 0; j < columnas - 1; j++) {
                
                // Sumamos los 4 elementos de la submatriz 2x2 actual
                int sumaActual = matriz[i][j]     + matriz[i][j + 1] +
                                 matriz[i + 1][j] + matriz[i + 1][j + 1];

                // Actualizamos la suma máxima si la suma actual es mayor
                if (sumaActual > sumaMaxima) {
                    sumaMaxima = sumaActual;
                }
            }
        }

        return sumaMaxima;
    }

    public static void main(String[] args) {
        // Ejemplo de la imagen:
        // [2, 5, 7]
        // [3, 4, 5]
        // [4, 1, 3]
        int[][] matrizEjemplo = {
            {2, 5, 7},
            {3, 4, 5},
            {4, 1, 3}
        };

        int resultado = maxSumaSubmatriz2x2(matrizEjemplo);
        System.out.println("La suma máxima de submatriz 2x2 es: " + resultado); 
        // Imprime: 21
    }
}