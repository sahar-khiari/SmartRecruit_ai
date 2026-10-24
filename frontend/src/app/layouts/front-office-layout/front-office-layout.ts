import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FrontOfficeTopbar } from './components/front-office-topbar/front-office-topbar';
import { FrontOfficeFooter } from './components/front-office-footer/front-office-footer';

/**
 * Layout front-office CANDIDATE / RECRUITER.
 * Topbar horizontale et footer, sans aucune sidebar d'administration.
 */
@Component({
  selector: 'app-front-office-layout',
  imports: [RouterOutlet, FrontOfficeTopbar, FrontOfficeFooter],
  templateUrl: './front-office-layout.html',
  styleUrl: './front-office-layout.scss',
})
export class FrontOfficeLayout {}
