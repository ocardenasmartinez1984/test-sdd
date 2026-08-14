import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Dispatch, DispatchStatus } from '../models/models';

@Injectable({ providedIn: 'root' })
export class DespachoService {
  private readonly apiUrl = '/api/despachos';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Dispatch[]> {
    return this.http.get<Dispatch[]>(this.apiUrl);
  }

  getByOrderId(orderId: string): Observable<Dispatch> {
    return this.http.get<Dispatch>(`${this.apiUrl}/order/${orderId}`);
  }

  getByTracking(trackingNumber: string): Observable<Dispatch> {
    return this.http.get<Dispatch>(`${this.apiUrl}/tracking/${trackingNumber}`);
  }

  getByStatus(status: DispatchStatus): Observable<Dispatch[]> {
    return this.http.get<Dispatch[]>(`${this.apiUrl}/status/${status}`);
  }

  updateStatus(id: string, status: DispatchStatus): Observable<Dispatch> {
    const params = new HttpParams().set('status', status);
    return this.http.put<Dispatch>(`${this.apiUrl}/${id}/status`, null, { params });
  }
}
