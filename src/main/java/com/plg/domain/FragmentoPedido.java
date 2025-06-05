package com.plg.domain;

class FragmentoPedido {
    private Pedido pedido;
    private double volumenAsignado;
    
    public FragmentoPedido(Pedido pedido, double volumenAsignado) {
        this.pedido = pedido;
        this.volumenAsignado = volumenAsignado;
    }
    
    public boolean esCompleto() {
        return Math.abs(volumenAsignado - pedido.getVolumenM3()) < 0.01;
    }
    
    public double getPorcentajeDelPedido() {
        return volumenAsignado / pedido.getVolumenM3() * 100.0;
    }
    
    // Getters
    public Pedido getPedido() { return pedido; }
    public double getVolumenAsignado() { return volumenAsignado; }
}
